package edu.berkeley.registry.model

class SOR implements Serializable {
    Short id
    String name

    static hasMany = [sorObjects: SORObject]

    static constraints = {
        name nullable: false, unique: true
    }

    static mapping = {
        table name: 'SOR'
        id column: 'sorId', type: "short", sqlType: 'SMALLINT', generator: 'sequence', params: [sequence: 'sor_seq']
        version false
        name column: 'sorName', sqlType: 'VARCHAR(64)'
    }
}
