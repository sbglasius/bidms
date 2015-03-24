package edu.berkeley.registry.model

class PersonName {

    Long id
    Person uid
    NameType nameType
    String honorific
    String givenName
    String middleName
    String surName
    String suffix
    String fullName

    static constraints = {
        honorific nullable: true
        givenName nullable: true
        middleName nullable: true
        surName nullable: true
        suffix nullable: true
        fullName nullable: true
    }

    static mapping = {
        version false
        id column: 'id', sqlType: 'BIGINT'
        honorific sqlType: 'VARCHAR(32)'
        givenName sqlType: 'VARCHAR(127)'
        middleName sqlType: 'VARCHAR(127)'
        surName sqlType: 'VARCHAR(127)'
        suffix sqlType: 'VARCHAR(32)'
        fullName sqlType: 'VARCHAR(255)'
    }
}
