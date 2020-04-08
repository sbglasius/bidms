package edu.berkeley.registry.model

import edu.berkeley.calnet.groovy.transform.LogicalEqualsAndHashCode
import edu.berkeley.registry.model.types.DelegateProxyTypeEnum
import edu.berkeley.util.domain.transform.ConverterConfig
import org.hibernate.FetchMode

@ConverterConfig(excludes = ["person", "delegateProxySorObject", "proxyForIdentifier", "proxyForPerson"])
@LogicalEqualsAndHashCode(
        excludes = ["id", "belongsTo", "constraints", "mapping", "transients", "version", "person", "proxyForIdentifier", "proxyForPerson", "proxyForPersonUid"],
        changeCallbackClass = DelegateProxyHashCodeChangeCallback
)
class DelegateProxy implements Comparable {

    static class DelegateProxyHashCodeChangeCallback extends PersonCollectionHashCodeChangeHandler<DelegateProxy> {
        DelegateProxyHashCodeChangeCallback() {
            super("delegations")
        }
    }

    Long id // this id is internal, generated by the sequence

    DelegateProxyType delegateProxyType

    // information about the proxy delegate
    static belongsTo = [person: Person] // the Person object (and thus uid) of the proxy delegate
    String sourceProxyId // primary key of the delegate proxy in the source system (ex: the CS_DELEGATE SCC_DA_PRXY_ID)
    SORObject delegateProxySorObject // The optional SORObject the delegate data is sourced from
    String delegateProxySecurityKey // The security key generated for the proxy to claim an account (ex: the CS_DELEGATE SCC_DA_SECURTY_KEY)

    // information about the person the delegate is a proxy for
    String proxyForId // the source system identifier this delegate is a proxy for (ex: the CS_DELEGATE EMPLID)

    /**
     * Lazy loads the proxyForIdentifier
     * @return
     */
    Identifier getProxyForIdentifier() {
        String identifierTypeName = DelegateProxyTypeEnum.getEnum(delegateProxyType).identifierTypeEnum.name
        IdentifierType identifierType = IdentifierType.findByIdName(identifierTypeName)
        Identifier identifier = Identifier.findByIdentifierTypeAndIdentifier(identifierType, proxyForId)
        return identifier
    }

    /**
     * Lazy loads the proxyForPerson
     * @return
     */
    Person getProxyForPerson() {
        def identifier = getProxyForIdentifier()
        identifier?.person
    }

    String getProxyForPersonUid() {
        return proxyForPerson?.uid
    }

    static transients = ['proxyForIdentifier', 'proxyForPerson', 'proxyForPersonUid']

    static constraints = {
        delegateProxySorObject nullable: true
        delegateProxySecurityKey nullable: true
    }

    static mapping = {
        table name: "DelegateProxy"
        version false
        id column: 'id', generator: 'sequence', params: [sequence: 'DelegateProxy_seq'], sqlType: 'BIGINT'
        person column: 'uid', sqlType: 'VARCHAR(64)'
        delegateProxyType column: 'delegateProxyTypeId', sqlType: 'SMALLINT', fetch: FetchMode.JOIN
        delegateProxySorObject column: 'delegateProxySorObjectId', sqlType: 'BIGINT'
        sourceProxyId column: 'sourceProxyId', sqlType: 'VARCHAR(64)'
        delegateProxySecurityKey column: 'delegateProxySecurityKey', sqlType: 'VARCHAR(64)'
        proxyForId column: 'proxyForId', sqlType: 'VARCHAR(64)'
    }

    int compareTo(obj) {
        return hashCode() <=> obj?.hashCode()
    }
}
