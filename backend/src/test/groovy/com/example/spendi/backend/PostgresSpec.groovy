package com.example.spendi.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.YearMonth
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier
import static com.example.spendi.backend.Models.*

@Tag('postgres')
class PostgresSpec extends Specification {
    @Shared HikariDataSource source
    @Shared EmbeddedPostgres embedded
    @Shared FinancialRepository repository
    @Shared def db
    @Shared def key = new RSAKeyGenerator(2048).keyID('integration-key').generate()
    @Shared ApiServer server
    @Shared String base
    @Shared HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    def setupSpec() {
        def config = new HikariConfig()
        if (Boolean.getBoolean('spendi.test.embedded')) {
            embedded = EmbeddedPostgres.builder().setPort(0).setServerConfig('listen_addresses', '127.0.0.1').start()
            DSL.using(embedded.postgresDatabase, SQLDialect.POSTGRES).execute('CREATE DATABASE spendi_test')
            config.jdbcUrl = embedded.getJdbcUrl('postgres', 'spendi_test')
            config.username = 'postgres'
            config.password = ''
        } else {
            assert System.getenv('SPENDI_TEST_DB_URL') ==~ /jdbc:postgresql:\/\/[^\/]+\/spendi_test(?:\?.*)?/: 'Use a disposable database named spendi_test; tests truncate its tables'
            config.jdbcUrl = System.getenv('SPENDI_TEST_DB_URL')
            config.username = System.getenv('SPENDI_TEST_DB_USER')
            config.password = System.getenv('SPENDI_TEST_DB_PASSWORD')
        }
        config.maximumPoolSize = 8
        source = new HikariDataSource(config)
        Main.migrate(source)
        db = DSL.using(source, SQLDialect.POSTGRES)
        repository = new FinancialRepository(source)
        def auth = new JwtAuthenticator('https://test-issuer.example', 'spendi-api', new ImmutableJWKSet(new JWKSet(key.toPublicJWK())))
        server = new ApiServer('127.0.0.1', 0, repository, auth)
        server.start()
        base = 'http://127.0.0.1:' + server.port()
    }
    def cleanupSpec() {
        server?.close()
        client?.close()
        source?.close()
        embedded?.close()
    }
    def setup() {
        db.execute('TRUNCATE TABLE transactions, budgets, idempotency_requests')
    }
    static TransactionWrite expense(String amount = '25.50', Long version = null, String category = 'Food', String date = '2027-07-15') {
        new TransactionWrite(amount, 'EXPENSE', category, date, 'Lunch', version)
    }
    static YearMonth july() { YearMonth.of(2027, 7) }
    String token(String subject) {
        def claims = new JWTClaimsSet.Builder().issuer('https://test-issuer.example').audience('spendi-api')
                .subject(subject).expirationTime(new Date(System.currentTimeMillis() + 120000)).build()
        def jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID('integration-key').build(), claims)
        jwt.sign(new RSASSASigner(key))
        jwt.serialize()
    }
    HttpResponse<String> request(String method, String path, Object body = null, String subject = 'alice', String idempotency = null, Map extraHeaders = [:]) {
        def builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
        if (subject != null) builder.header('Authorization', 'Bearer ' + token(subject))
        if (idempotency != null) builder.header('Idempotency-Key', idempotency)
        if (body != null) builder.header('Content-Type', 'application/json')
        extraHeaders.each { name, value -> builder.header(name.toString(), value.toString()) }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.write(body)))
        client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    def 'migrations create PostgreSQL constraints and can run again'() {
        when:
        Main.migrate(source)
        db.execute("INSERT INTO transactions(id, owner_subject, amount_minor, kind, category, occurred_on) VALUES (?, 'alice', 0, 'EXPENSE', 'Food', '2027-07-01')", UUID.randomUUID())
        then:
        thrown(DataAccessException)
        repository.ready()
    }

    def 'concurrent retries create exactly one financial record and one key reservation'() {
        given:
        def executor = Executors.newVirtualThreadPerTaskExecutor()
        when:
        def pending = (1..16).collect { CompletableFuture.supplyAsync({ repository.create('alice', 'same-request', expense()) } as Supplier, executor) }
        def results = pending.collect { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
        then:
        results.collect { it.transaction().id() }.unique().size() == 1
        results.count { !it.replayed() } == 1
        db.fetchCount(DSL.table('transactions')) == 1
        db.fetchCount(DSL.table('idempotency_requests')) == 1
        cleanup:
        executor.close()
    }

    def 'same key with different data conflicts and keys are scoped to each owner'() {
        given:
        def original = repository.create('alice', 'request-1', expense()).transaction()
        when:
        repository.create('alice', 'request-1', expense('30'))
        then:
        def error = thrown(ApiException)
        error.status == 409
        repository.get('alice', original.id()).amount() == '25.50'
        when:
        def other = repository.create('bob', 'request-1', expense()).transaction()
        then:
        other.id() != original.id()
    }

    def 'database failure rolls back the key reservation and permits a later retry'() {
        given:
        db.execute("ALTER TABLE transactions ADD CONSTRAINT test_injected_failure CHECK (category <> 'FAIL_AFTER_RESERVATION')")
        when:
        repository.create('alice', 'retry-after-failure', expense('25.50', null, 'FAIL_AFTER_RESERVATION'))
        then:
        thrown(DataAccessException)
        db.fetchCount(DSL.table('transactions')) == 0
        db.fetchCount(DSL.table('idempotency_requests')) == 0
        when:
        db.execute('ALTER TABLE transactions DROP CONSTRAINT test_injected_failure')
        def result = repository.create('alice', 'retry-after-failure', expense('25.50', null, 'FAIL_AFTER_RESERVATION'))
        then:
        !result.replayed()
        cleanup:
        db.execute('ALTER TABLE transactions DROP CONSTRAINT IF EXISTS test_injected_failure')
    }

    def 'another owner cannot read update delete or list a transaction'() {
        given:
        def original = repository.create('alice', 'alice-expense', expense()).transaction()
        when:
        repository.get('bob', original.id())
        then:
        def readError = thrown(ApiException)
        readError.status == 404
        when:
        repository.update('bob', original.id(), expense('1', 1L))
        then:
        def updateError = thrown(ApiException)
        updateError.status == 404
        when:
        repository.delete('bob', original.id(), 1)
        then:
        def deleteError = thrown(ApiException)
        deleteError.status == 404
        repository.list('bob', july(), 50, 0).items().empty
        repository.get('alice', original.id()).amount() == '25.50'
    }

    def 'stale versions cannot overwrite or delete a newer edit'() {
        given:
        def original = repository.create('alice', 'expense', expense()).transaction()
        when:
        def updated = repository.update('alice', original.id(), expense('30', 1L))
        then:
        updated.version() == 2
        when:
        repository.update('alice', original.id(), expense('999', 1L))
        then:
        thrown(ApiException)
        when:
        repository.delete('alice', original.id(), 1)
        then:
        thrown(ApiException)
        repository.get('alice', original.id()).amount() == '30.00'
        when:
        repository.delete('alice', original.id(), 2)
        then:
        repository.create('alice', 'expense', expense()).transaction() == original
        db.fetchCount(DSL.table('transactions')) == 0
    }

    def 'summaries include exactly the requested month and year and never overflow long'() {
        given:
        repository.create('alice', 'large-1', expense('92233720368547758.07'))
        repository.create('alice', 'large-2', expense('92233720368547758.07'))
        repository.create('alice', 'last-month', expense('100', null, 'Food', '2027-06-30'))
        repository.create('alice', 'next-month', expense('100', null, 'Food', '2027-08-01'))
        repository.create('alice', 'other-year', expense('100', null, 'Food', '2026-07-15'))
        repository.create('bob', 'other-owner', expense('100'))
        repository.saveBudget('alice', july(), new BudgetWrite('100', null))
        when:
        def summary = repository.summary('alice', july())
        then:
        summary.expenses() == '184467440737095516.14'
        summary.balance() == '-184467440737095516.14'
        summary.remaining() == '-184467440737095416.14'
        repository.list('alice', july(), 1, 0).items().size() == 1
        repository.list('alice', july(), 1, 1).items().size() == 1
        repository.list('alice', july(), 1, 2).items().empty
    }

    def 'budget ownership and optimistic updates are enforced'() {
        given:
        repository.saveBudget('alice', july(), new BudgetWrite('100', null))
        when:
        repository.budget('bob', july())
        then:
        thrown(ApiException)
        when:
        repository.saveBudget('alice', july(), new BudgetWrite('1', null))
        then:
        thrown(ApiException)
        when:
        def updated = repository.saveBudget('alice', july(), new BudgetWrite('150', 1L))
        then:
        updated.version() == 2
        when:
        repository.saveBudget('alice', july(), new BudgetWrite('1', 1L))
        then:
        thrown(ApiException)
        repository.budget('alice', july()).amount() == '150.00'
        repository.summary('bob', july()).remaining() == null
    }

    def 'HTTP routes cover authenticated CRUD budgets and summaries'() {
        when:
        def created = request('POST', '/v1/transactions', expense(), 'alice', 'http-expense')
        def saved = Json.read(created.body(), Map)
        def replay = request('POST', '/v1/transactions', expense(), 'alice', 'http-expense')
        then:
        created.statusCode() == 201
        created.headers().firstValue('Location').get().endsWith(saved.id)
        replay.body() == created.body()
        replay.headers().firstValue('Idempotency-Replayed').get() == 'true'
        request('GET', '/v1/transactions/' + saved.id, null, 'bob').statusCode() == 404
        request('PUT', '/v1/transactions/' + saved.id, expense('30', 1L)).statusCode() == 200
        request('PUT', '/v1/budgets/2027-07', new BudgetWrite('100', null)).statusCode() == 201
        request('GET', '/v1/budgets/2027-07').statusCode() == 200
        Json.read(request('GET', '/v1/summaries/2027-07').body(), Map).remaining == '70.00'
        request('DELETE', '/v1/transactions/' + saved.id + '?version=2').statusCode() == 204
        request('GET', '/v1/transactions/' + saved.id).statusCode() == 404
    }

    def 'HTTP rejects unauthenticated invalid and oversized input with explicit errors'() {
        expect:
        request('GET', '/health/live', null, null).statusCode() == 200
        request('GET', '/health/ready', null, null).statusCode() == 200
        request('GET', '/v1/transactions?month=2027-07', null, null).statusCode() == 401
        request('POST', '/v1/transactions', expense()).statusCode() == 400
        request('POST', '/v1/transactions', [amount:'1',type:'EXPENSE',category:'Food',date:'2027-07-01',owner_subject:'bob'], 'alice', 'owner-injection').statusCode() == 400
        request('GET', '/v1/transactions?month=2027-13').statusCode() == 400
        request('GET', '/v1/transactions?month=2027-07&month=2027-08').statusCode() == 400
        request('GET', '/v1/transactions?month=2027-07&limit=101').statusCode() == 400
        request('POST', '/v1/transactions', [note:'x' * 17000], 'alice', 'too-large').statusCode() == 413
        request('PATCH', '/v1/transactions').statusCode() == 405
    }
}
