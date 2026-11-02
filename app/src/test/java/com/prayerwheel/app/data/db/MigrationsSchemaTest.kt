package com.prayerwheel.app.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Checks that the hand-written migration SQL turns the exported v(N) schema
 * into exactly the exported v(N+1) schema (tables, columns with affinity and
 * nullability, indices). A mismatch would make Room throw on first launch
 * after the update — and since destructive fallback is limited to v1, that
 * is a crash, not silent data loss, but still unshippable.
 */
class MigrationsSchemaTest {

    private data class Column(val affinity: String, val notNull: Boolean)

    private data class Schema(
        val tables: MutableMap<String, MutableMap<String, Column>>,
        val createSql: Map<String, String>,
        val indices: MutableSet<String>
    )

    private fun schemaFile(version: Int): File {
        val rel = "schemas/com.prayerwheel.app.data.db.AppDatabase/$version.json"
        return listOf(File(rel), File("app/$rel")).firstOrNull { it.exists() }
            ?: error("Exported schema $rel not found (working dir ${File(".").absolutePath})")
    }

    private fun load(version: Int): Schema {
        val db = JSONObject(schemaFile(version).readText()).getJSONObject("database")
        assertEquals(version, db.getInt("version"))
        val entities = db.getJSONArray("entities")
        val tables = linkedMapOf<String, MutableMap<String, Column>>()
        val createSql = linkedMapOf<String, String>()
        val indices = linkedSetOf<String>()
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val name = e.getString("tableName")
            createSql[name] = e.getString("createSql").replace("\${TABLE_NAME}", name)
            val fields = e.getJSONArray("fields")
            tables[name] = (0 until fields.length()).associateTo(linkedMapOf()) { j ->
                val f = fields.getJSONObject(j)
                f.getString("columnName") to Column(f.getString("affinity"), f.optBoolean("notNull", false))
            }
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) {
                indices += idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name)
            }
        }
        return Schema(tables, createSql, indices)
    }

    private val addColumn = Regex(
        """ALTER TABLE `?(\w+)`? ADD COLUMN `?(\w+)`? (\w+)( NOT NULL)?( DEFAULT .+)?""",
        RegexOption.IGNORE_CASE
    )
    private val createTable = Regex("""CREATE TABLE IF NOT EXISTS `(\w+)`.*""")
    private val createIndex = Regex("""CREATE (UNIQUE )?INDEX IF NOT EXISTS .*""")
    private val dataStatement = Regex("""(?:UPDATE|INSERT INTO) `(\w+)`.*""", RegexOption.DOT_MATCHES_ALL)

    private fun applyMigration(from: Schema, to: Schema, statements: List<String>) {
        for (sql in statements) {
            addColumn.matchEntire(sql)?.let { m ->
                val (table, column, type, notNull, default) = m.destructured
                val cols = from.tables[table] ?: fail("ALTER of unknown table $table") as Nothing
                assertTrue("NOT NULL column $table.$column needs a DEFAULT", notNull.isEmpty() || default.isNotEmpty())
                cols[column] = Column(type.uppercase(), notNull.isNotEmpty())
                return@let
            } ?: createTable.matchEntire(sql)?.let { m ->
                val table = m.groupValues[1]
                assertEquals("CREATE TABLE $table must match exported schema", to.createSql[table], sql)
                from.tables[table] = LinkedHashMap(to.tables.getValue(table))
            } ?: createIndex.matchEntire(sql)?.let {
                assertTrue("Index not in exported schema: $sql", sql in to.indices)
                from.indices += sql
            } ?: dataStatement.matchEntire(sql)?.let { m ->
                // Data-only: must target an existing table and not change the schema.
                assertTrue("Data statement on unknown table: $sql", m.groupValues[1] in from.tables)
            } ?: fail("Unrecognized migration statement: $sql")
        }
    }

    @Test
    fun migration4to5_producesExportedV5Schema() {
        val v4 = load(4)
        val v5 = load(5)
        applyMigration(v4, v5, MIGRATION_4_5_SQL)
        assertEquals(v5.tables.keys, v4.tables.keys)
        for ((table, cols) in v5.tables) {
            assertEquals("columns of $table", cols, v4.tables[table])
        }
        assertEquals(v5.indices, v4.indices)
    }
}
