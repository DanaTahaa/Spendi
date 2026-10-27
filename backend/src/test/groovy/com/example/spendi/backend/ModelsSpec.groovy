package com.example.spendi.backend

import spock.lang.Specification
import spock.lang.Unroll
import static com.example.spendi.backend.Models.*

class ModelsSpec extends Specification {
    @Unroll
    def 'valid financial amount #amount converts to #minor minor units'() {
        expect:
        new TransactionWrite(amount, 'EXPENSE', ' Food ', '2028-02-29', null, null).validate().amountMinor() == minor
        where:
        amount                  | minor
        '25.50'                 | 2550L
        '٢٥٫٥٠'                 | 2550L
        '0.01'                  | 1L
        '92233720368547758.07'   | Long.MAX_VALUE
    }

    @Unroll
    def 'invalid financial amount #amount is rejected'() {
        when:
        new TransactionWrite(amount, 'EXPENSE', 'Food', '2027-07-01', '', null).validate()
        then:
        def error = thrown(ApiException)
        error.status == 400
        where:
        amount << [null, '', '-1', '0', '1.234', '1e2', '92233720368547758.08']
    }

    @Unroll
    def 'invalid transaction fields are rejected'() {
        when:
        new TransactionWrite('1', type, category, date, note, null).validate()
        then:
        thrown(ApiException)
        where:
        type      | category  | date         | note
        'REFUND'  | 'Food'    | '2027-07-01' | ''
        'EXPENSE' | null      | '2027-07-01' | ''
        'EXPENSE' | ' '       | '2027-07-01' | ''
        'EXPENSE' | 'x' * 61  | '2027-07-01' | ''
        'EXPENSE' | 'Food'    | '2027-02-29' | ''
        'EXPENSE' | 'Food'    | '1899-12-31' | ''
        'EXPENSE' | 'Food'    | '2027-7-1'   | ''
        'EXPENSE' | 'Food'    | null         | ''
        'EXPENSE' | 'Food'    | '2027-07-01' | 'x' * 1001
    }

    def 'JSON rejects client supplied ownership and trailing objects'() {
        when:
        Json.read(json, TransactionWrite)
        then:
        thrown(ApiException)
        where:
        json << ['{"owner_subject":"another-user"}', '{} {}', 'null', '{', '{"amount":1.23}',
                 '{"version":"1"}', '{"version":1.5}', '{"amount":"1","amount":"2"}']
    }

    def 'month and update version boundaries are explicit'() {
        expect:
        month('2028-02').toString() == '2028-02'
        expectedVersion(1L) == 1
        when:
        month('2027-13')
        then:
        thrown(ApiException)
        when:
        expectedVersion(0L)
        then:
        thrown(ApiException)
    }
}
