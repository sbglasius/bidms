package edu.berkeley.match

import edu.berkeley.registry.model.Person
import grails.transaction.Transactional
import org.springframework.http.HttpStatus

@Transactional(readOnly = true)
class MatchClientService {
    def restClient
    def grailsApplication

    /**
     * Call the match-engine to see if the database has a match on an existing record.
     * The match-engine configuration determins if there is no match, a single (canonical) match, or a partial match
     *
     * @param a map containing the some or all of the following properties (in this format)
     * [
     *      systemOfRecord: 'SIS', sorPrimaryKey: 'SIS00001', fullName: 'lastName, firstName middleName', givenName: 'firstName', middleName: 'middleName', lastName: 'lastName',
     *      dateOfBirth: 'DOB', email: 'some@email.com', socialSecurityNumber: 'SSN', otherIds: [studentId: 'abc', employeeId: 'xyz'], matchOnly: false
     * ]
     * @return PersonMatch object
     * @throws RuntimeException a runtime exception if the match-engine returns other status codes than NOT_FOUND, OK or MULTIPLE_CHOICES
     */
    PersonMatch match(Map<String, Object> p) {
        String matchUrl = grailsApplication.config.rest.matchEngine.url
        def jsonMap = buildJsonMap(p)
        def response = restClient.post(matchUrl) {
            accept 'application/json'
            contentType "application/json"
            json jsonMap
        }
        // The difference between OK and FOUND (I think) is that OK
        // indicates the SORObject matches up to an existing uid, where
        // as FOUND indicates the SORObject is already matched.  See
        // difference between the ExactMatchResponse (OK) and
        // ExistingMatchResponse (FOUND) in ucb-match.
        switch (response.statusCode) {
            case HttpStatus.NOT_FOUND:
                // matchOnly=true on input will cause person not to go to newUid queue
                return new PersonNoMatch(matchOnly: jsonMap.matchOnly as Boolean)
            case HttpStatus.OK:
                return exactMatch(response.json)
            case HttpStatus.FOUND:
                return existingMatch(response.json)
            case HttpStatus.MULTIPLE_CHOICES:
                return partialMatch(response.json)
            default:
                log.error("Got wrong return code from match engine..")
                // TODO: Determin what to do in this situation
                throw new RuntimeException("Got wrong return code from match engine: $response.statusCode.reasonPhrase ($response.statusCode) - ${response.text}")
        }

    }

    private static PersonExactMatch exactMatch(def json) {
        // Person object is not to be changed
        def person = Person.findByUid(json.matchingRecord.referenceId as String)
        new PersonExactMatch(person: person)
    }

    private static PersonExistingMatch existingMatch(def json) {
        def person = Person.findByUid(json.matchingRecord.referenceId as String)
        new PersonExistingMatch(person: person)
    }

    private static PersonPartialMatches partialMatch(def json) {
        def people = json.partialMatchingRecords*.referenceId.collect {
            // Person object is not to be changed
            Person.findByUid(it as String)
        }
        new PersonPartialMatches(people: people)
    }

    /**
     * Map input parameters to a Match-Engine request
     * @param params
     * @return
     */
    private static Map buildJsonMap(Map<String, Object> params) {
        def map = [systemOfRecord: params.systemOfRecord, identifier: params.sorPrimaryKey]

        // Copy top level properties
        ['dateOfBirth', 'email', 'matchOnly'].each {
            if (params[it]) {
                map[it] = params[it]
            }
        }

        // Copy name attributes to names structure
        def name = ['givenName', 'middleName', 'surName', 'fullName'].collectEntries {
            [it, params[it]]
        }.findAll { it.value }

        if (name) {
            name.type = "official"
            map.names = [name]
        }

        if (params.socialSecurityNumber || params.otherIds) {

            // Copy other identifiers (comes in a map) to the identifiers list
            map.identifiers = params.otherIds?.collect { type, value ->
                [
                        type      : type,
                        identifier: value
                ]
            } ?: []

            // Finally add socialSecurityNumber if present
            if (params.socialSecurityNumber) {
                map.identifiers << [type: "socialSecurityNumber", identifier: params.socialSecurityNumber]
            }
        }

        return map
    }
}
