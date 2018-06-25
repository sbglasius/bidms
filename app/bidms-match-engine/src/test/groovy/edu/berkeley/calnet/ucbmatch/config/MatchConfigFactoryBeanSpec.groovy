package edu.berkeley.calnet.ucbmatch.config

import spock.lang.Specification

import static edu.berkeley.calnet.ucbmatch.config.MatchConfig.MatchType.*

class MatchConfigFactoryBeanSpec extends Specification {
    def "test that parseConfig will return a valid config"() {
        setup:
        def config = """
            import static edu.berkeley.calnet.ucbmatch.config.MatchConfig.MatchType.*

            matchTable('myTableName')

            referenceId {
                responseType = "enterprise"
            }

            attributes {
                'sor' {
                    description = "sor description"
                    column = "sorColumn"
                }
                'name' {
                    description = "name description"
                    column = "nameColumn"
                }
            }
            confidences {
                canonical "Custom Name", sor: EXACT
                canonical sor: EXACT, name: SUBSTRING
                potential sor: EXACT, name: DISTANCE
            }
        """

        when:
        def matchConfig = MatchConfigFactoryBean.parseConfig(config)

        then:
        matchConfig.matchTable == 'myTableName'
        matchConfig.matchReference.responseType == 'enterprise'
        matchConfig.matchAttributeConfigs.name == ['sor','name']
        matchConfig.canonicalConfidences*.confidence == [[sor: EXACT],[sor: EXACT,name: SUBSTRING]]
        matchConfig.canonicalConfidences*.ruleName == ["Custom Name","Canonical #2"]
        matchConfig.potentialConfidences*.confidence == [[sor:EXACT,name:DISTANCE]]
        matchConfig.potentialConfidences*.ruleName == ["Potential #1"]
    }
}