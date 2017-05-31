/*
 * Copyright (c) 2017, Regents of the University of California and
 * contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package edu.berkeley.bidms.connector.ldap

import edu.berkeley.bidms.connector.ldap.event.LdapDeleteEventCallback
import edu.berkeley.bidms.connector.ldap.event.LdapInsertEventCallback
import edu.berkeley.bidms.connector.ldap.event.LdapRenameEventCallback
import edu.berkeley.bidms.connector.ldap.event.LdapUpdateEventCallback
import org.springframework.ldap.core.DirContextAdapter
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.core.support.LdapContextSource
import software.apacheds.embedded.EmbeddedLdapServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.naming.Name

import static org.springframework.ldap.query.LdapQueryBuilder.query

class LdapConnectorSpec extends Specification {
    @Shared
    EmbeddedLdapServer embeddedLdapServer

    @Shared
    LdapContextSource ldapContextSource

    @Shared
    LdapTemplate ldapTemplate

    LdapInsertEventCallback insertEventCallback = Mock(LdapInsertEventCallback)
    LdapUpdateEventCallback updateEventCallback = Mock(LdapUpdateEventCallback)
    LdapRenameEventCallback renameEventCallback = Mock(LdapRenameEventCallback)
    LdapDeleteEventCallback deleteEventCallback = Mock(LdapDeleteEventCallback)

    LdapConnector ldapConnector = new LdapConnector([
            insertEventCallbacks: [insertEventCallback],
            updateEventCallbacks: [updateEventCallback],
            renameEventCallbacks: [renameEventCallback],
            deleteEventCallbacks: [deleteEventCallback]
    ])

    void setupSpec() {
        this.embeddedLdapServer = new EmbeddedLdapServer() {
            @Override
            String getBasePartitionName() {
                return "berkeley"
            }

            @Override
            String getBaseStructure() {
                return "dc=berkeley,dc=edu"
            }
        }
        embeddedLdapServer.init()

        this.ldapContextSource = new LdapContextSource()
        ldapContextSource.with {
            userDn = "uid=admin,ou=system"
            password = "secret"
            url = "ldap://localhost:10389"
        }
        ldapContextSource.afterPropertiesSet()
        this.ldapTemplate = new LdapTemplate(ldapContextSource)
        ldapTemplate.afterPropertiesSet()
    }

    void cleanupSpec() {
        embeddedLdapServer.destroy()
    }

    void setup() {
        ldapConnector.ldapTemplate = ldapTemplate
    }

    void addOu(String ou) {
        Name dnName = ldapConnector.buildDnName("ou=$ou,dc=berkeley,dc=edu")
        ldapTemplate.bind(dnName, null, ldapConnector.buildAttributes([
                ou         : ou,
                objectClass: ["top", "organizationalUnit"]
        ]))
    }

    void deleteOu(String ou) {
        ldapTemplate.unbind(ldapConnector.buildDnName("ou=$ou,dc=berkeley,dc=edu"))
    }

    void addTestEntry(String dn, String uid, String cn = null) {
        Name dnName = ldapConnector.buildDnName(dn)
        ldapTemplate.bind(dnName, null, ldapConnector.buildAttributes([
                uid        : uid,
                objectClass: ["top", "person", "inetOrgPerson"],
                sn         : "User",
                cn         : cn ?: "Test User",
                description: "initial test"
        ]))
    }

    void deleteDn(String dn) {
        ldapTemplate.unbind(ldapConnector.buildDnName(dn))
    }

    List<Map<String, Object>> searchForUid(String uid) {
        return ldapTemplate.search(query()
                .where("objectClass").is("person")
                .and("uid").is(uid),
                ldapConnector.toMapContextMapper)
    }

    @Unroll("#description")
    void "test keepExistingAttributesWhenUpdating"() {
        given:
        UidObjectDefinition objDef = new UidObjectDefinition("person", keepExistingAttributesWhenUpdating, true, appendAttrs as String[])

        when:
        addOu("people")
        String dn = "uid=1,ou=people,dc=berkeley,dc=edu"
        String uid = "1"
        String eventId = "eventId"
        // create
        boolean didCreate = ldapConnector.persist(eventId, objDef, [
                dn         : dn,
                uid        : uid,
                objectClass: ["top", "person", "inetOrgPerson", "organizationalPerson"],
                sn         : "User",
                cn         : "Test User",
                description: "initial test",
                mail       : ["test@berkeley.edu"]
        ], false)
        // update - description is kept or removed based on the value of keepExistingAttributesWhenUpdating in objDef
        boolean didUpdate = ldapConnector.persist(eventId, objDef, [
                dn         : dn,
                uid        : uid,
                objectClass: ["top", "person", "inetOrgPerson", "organizationalPerson"],
                sn         : "User",
                cn         : "Test User",
                mail       : ["test2@berkeley.edu"]
        ] + (updateDescAttr || nullOutDescAttr ? ["description": (nullOutDescAttr ? null : updateDescAttr)] : [:]), false)
        List<Map<String, Object>> retrieved = searchForUid(uid)

        and: "cleanup"
        deleteDn(dn)
        deleteOu("people")

        then:
        didCreate
        didUpdate
        retrieved.size() == 1
        retrieved.first().description == expectedDescription
        retrieved.first().mail == expectedMail

        where:
        description                                                                               | keepExistingAttributesWhenUpdating | updateDescAttr | nullOutDescAttr | appendAttrs | expectedDescription | expectedMail
        "keepExistingAttributesWhenUpdating=true"                                                 | true                               | null           | false           | null        | "initial test"      | "test2@berkeley.edu"
        "keepExistingAttributesWhenUpdating=false"                                                | false                              | null           | false           | null        | null                | "test2@berkeley.edu"
        "keepExistingAttributesWhenUpdating=true, update existing description"                    | true                               | "updated"      | false           | null        | "updated"           | "test2@berkeley.edu"
        "keepExistingAttributesWhenUpdating=true, update existing description and append to mail" | true                               | "updated"      | false           | ["mail"]    | "updated"           | ["test@berkeley.edu", "test2@berkeley.edu"]
        "keepExistingAttributesWhenUpdating=true, remove existing description by explicit null"   | true                               | null           | true            | null        | null                | "test2@berkeley.edu"
    }

    @Unroll("#description")
    void "test LdapConnector persistence"() {
        given:
        UidObjectDefinition uidObjectDef = new UidObjectDefinition("person", true, removeDupes, null)

        when:
        addOu("people")
        addOu("expired people")
        addOu("the middle")
        if (createFirst) {
            addTestEntry("uid=$uid,ou=people,dc=berkeley,dc=edu", uid)
            assert ((DirContextAdapter) ldapTemplate.lookup("uid=$uid,ou=people,dc=berkeley,dc=edu")).getStringAttribute("description") == "initial test"
        }
        if (createDupe) {
            addTestEntry("uid=$uid,ou=expired people,dc=berkeley,dc=edu", uid)
            assert ((DirContextAdapter) ldapTemplate.lookup("uid=$uid,ou=expired people,dc=berkeley,dc=edu")).getStringAttribute("description") == "initial test"
        }
        String eventId = "eventId"
        boolean isModified = ldapConnector.persist(eventId, uidObjectDef, [
                dn         : dn,
                uid        : uid,
                objectClass: ["top", "person", "inetOrgPerson", "organizationalPerson"],
                sn         : "User",
                cn         : "Test User",
                mail       : [],
                description: ["updated"]
        ], doDelete)

        List<Map<String, Object>> retrieved = searchForUid(uid)
        Map<String, Object> foundDn = retrieved.find {
            it.dn == dn
        }

        and: "cleanup"
        if (!doDelete) {
            // if the doDelete flag is set, that means we already deleted it
            deleteDn(dn)
        }
        if (createDupe && !removeDupes) {
            deleteDn("uid=$uid,ou=expired people,dc=berkeley,dc=edu")
        }
        deleteOu("people")
        deleteOu("expired people")
        deleteOu("the middle")

        then:
        isModified
        retrieved.size() == (!doDelete ? (createDupe && !removeDupes ? 2 : 1) : 0)
        (!doDelete ? foundDn.dn : null) == (!doDelete ? dn : null)
        (!doDelete ? foundDn.description : null) == (!doDelete ? "updated" : null)
        deletes * deleteEventCallback.success("eventId", uidObjectDef, uid, _)
        renames * renameEventCallback.success("eventId", uidObjectDef, uid, _, _)
        updates * updateEventCallback.success("eventId", uidObjectDef, uid, _, _, _, _)
        inserts * insertEventCallback.success("eventId", uidObjectDef, uid, _, _)

        where:
        description                                                | createFirst | createDupe | doDelete | removeDupes | uid | dn                                           | deletes | renames | updates | inserts
        "test creation"                                            | false       | false      | false    | true        | "1" | "uid=1,ou=people,dc=berkeley,dc=edu"         | 0       | 0       | 0       | 1
        "test update"                                              | true        | false      | false    | true        | "1" | "uid=1,ou=people,dc=berkeley,dc=edu"         | 0       | 0       | 1       | 0
        "test rename"                                              | true        | false      | false    | true        | "1" | "uid=1,ou=expired people,dc=berkeley,dc=edu" | 0       | 1       | 1       | 0
        "test update and remove nonmatching dupe"                  | true        | true       | false    | true        | "1" | "uid=1,ou=people,dc=berkeley,dc=edu"         | 1       | 0       | 1       | 0
        "test update and don't remove nonmatching dupe"            | true        | true       | false    | false       | "1" | "uid=1,ou=people,dc=berkeley,dc=edu"         | 0       | 0       | 1       | 0
        "test update with two dupes, rename one, delete the other" | true        | true       | false    | true        | "1" | "uid=1,ou=the middle,dc=berkeley,dc=edu"     | 1       | 1       | 1       | 0
        "test delete"                                              | true        | false      | true     | true        | "1" | "uid=1,ou=people,dc=berkeley,dc=edu"         | 1       | 0       | 0       | 0
        "test multi-delete"                                        | true        | true       | true     | true        | "1" | "uid=1,ou=people,dc=berkeley,dc=edu"         | 2       | 0       | 0       | 0
    }

    void "test persist return value on a non-modification"() {
        given:
        UidObjectDefinition objDef = new UidObjectDefinition("person", true, true, null)

        when:
        addOu("people")
        String dn = "uid=1,ou=people,dc=berkeley,dc=edu"
        String uid = "1"
        String eventId = "eventId"
        Map<String, Object> map = [
                dn         : dn,
                uid        : uid,
                objectClass: ["top", "person", "inetOrgPerson", "organizationalPerson"],
                sn         : "User",
                cn         : "Test User",
                description: "initial test"
        ]
        // create
        boolean didCreate = ldapConnector.persist(eventId, objDef, map, false)
        // update with same data such that no modification should occur
        boolean didUpdate = ldapConnector.persist(eventId, objDef, map, false)
        List<Map<String, Object>> retrieved = searchForUid(uid)

        and: "cleanup"
        deleteDn(dn)
        deleteOu("people")

        then:
        didCreate
        // no actual modification should have happened
        !didUpdate
        retrieved.size() == 1
        retrieved.first().description == "initial test"
    }

    @Unroll("#description")
    void "test LdapConnector persistence when primary key is not in DN and primary key changes"() {
        given:
        UidObjectDefinition uidObjectDef = new UidObjectDefinition("person", true, true, null)

        when:
        addOu("namespace")

        // create initial entry for cn with the primary key of createUid
        String dn = "cn=$cn,ou=namespace,dc=berkeley,dc=edu"
        addTestEntry(dn, createUid, cn)
        assert ((DirContextAdapter) ldapTemplate.lookup(dn)).getStringAttribute("description") == "initial test"

        // update the entry for cn, but change the primary key within the entry to updateUid
        String eventId = "eventId"
        boolean isModified = ldapConnector.persist(eventId, uidObjectDef, [
                dn         : dn,
                uid        : updateUid,
                objectClass: ["top", "person", "inetOrgPerson", "organizationalPerson"],
                sn         : "User",
                cn         : cn,
                mail       : [],
                description: ["updated"]
        ], false)

        List<Map<String, Object>> retrieved = searchForUid(updateUid)
        Map<String, Object> foundDn = retrieved.find {
            it.dn == dn
        }

        and: "cleanup"
        deleteDn(dn)
        deleteOu("namespace")

        then:
        isModified
        retrieved.size() == 1
        foundDn.dn == dn
        foundDn.description == "updated"
        1 * updateEventCallback.success("eventId", uidObjectDef, updateUid, _, _, _, _)

        where:
        description                           | cn         | createUid | updateUid
        "test update with primary key change" | "testName" | "1"       | "2"
    }
}
