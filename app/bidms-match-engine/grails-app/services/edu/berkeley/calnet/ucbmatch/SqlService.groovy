package edu.berkeley.calnet.ucbmatch

import grails.gorm.transactions.Transactional
import groovy.sql.Sql

import javax.sql.DataSource

@Transactional
class SqlService {
    DataSource dataSource_functionalDS
    DataSource dataSource

    Sql getSqlInstance() {
        new Sql(dataSource_functionalDS ?: dataSource)
    }
}
