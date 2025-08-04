/**
 * @author FangYan
 * ctime: 2024-10-21
 *
 * QPacketHelper.kt – build JSON → Map<Int,Any> → Protobuf bytes, then send.
 */
package moe.ono.hooks.protocol

import android.util.Log
import moe.ono.service.QQInterfaces
import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import moe.ono.BuildConfig
import moe.ono.config.CacheConfig
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.item.developer.QQPacketHelperC2CDisplayFixer
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.random.nextUInt

/* ───────────────────────────────────────────── */
/* ─────────────── Public helpers ────────────── */
/* ───────────────────────────────────────────── */

/**
 * Sends a message by constructing a JSON payload, encoding it to Protobuf,
 * and sending it via QQInterfaces.
 *
 * @param content    JSON string (the “element” block or other types later)
 * @param id         QQ-uid / Group-uin
 * @param isGroupMsg true if group message
 * @param type       currently only `"element"`
 */
@OptIn(ExperimentalSerializationApi::class)
fun sendMessage(
    content: String,
    id: String,
    isGroupMsg: Boolean,
    type: String,
    longmsg: Boolean,
    original: String
) {
    val TAG = BuildConfig.TAG
    val json = Json { ignoreUnknownKeys = true }

    try {
        var pbJson: JsonObject = buildBasePbContent(id, isGroupMsg)

        /* ── patch message element list ── */
        when (type) {
            "element" -> {
                Log.d("$TAG!pbcontent", content)

                val jsonElement = json.decodeFromString<JsonElement>(content)
                val updatedElems: List<JsonObject> = when (jsonElement) {
                    is JsonArray  -> jsonElement.filterIsInstance<JsonObject>()
                    is JsonObject -> listOf(jsonElement)
                    else          -> {
                        Log.e("$TAG!err", "Invalid JSON!")
                        return
                    }
                }

                val jsonArray = buildJsonArray {
                    updatedElems.forEach { add(it) }
                }

                pbJson = buildJsonObject {
                    pbJson.forEach { (k, v) ->
                        if (k == "3") {
                            val path1 = v.jsonObject["1"]!!.jsonObject.toMutableMap()
                            path1["2"] = jsonArray
                            put("3", buildJsonObject { put("1", JsonObject(path1)) })
                        } else put(k, v)
                    }
                }
            }

            else -> throw IllegalArgumentException("Unsupported type '$type'")
        }

        pbJson = buildJsonObject {
            pbJson.forEach { (k, v) -> put(k, v) }
            put("4", JsonPrimitive(Random.nextUInt()))
            put("5", JsonPrimitive(Random.nextUInt()))
        }

        Log.d("$TAG!pbcontent", "basePbContent = ${json.encodeToString(pbJson)}")

        val map      = parseJsonToMap(pbJson)
        val rawBytes = encodeMessage(map)

        QQInterfaces.sendBuffer("MessageSvc.PbSendMsg", true, rawBytes)
        if (getItem(QQPacketHelperC2CDisplayFixer::class.java).isEnabled) {
            if (longmsg) {
                CacheConfig.addPbSendMsgPacket(CacheConfig.PbSendMsgPacket(original, id))
            } else {
                CacheConfig.addPbSendMsgPacket(CacheConfig.PbSendMsgPacket(content, id))
            }
        }

    } catch (e: Exception) {
        Log.e("${BuildConfig.TAG}!err", "sendMessage failed: ${e.message}", e)
    }
}

/** Thin wrapper for arbitrary QQ commands built from JSON. */
fun sendPacket(cmd: String, content: String) =
    QQInterfaces.sendBuffer(cmd, true, buildMessage(content))

/** JSON string → protobuf bytes. */
fun buildMessage(content: String): ByteArray {
    val json = Json { ignoreUnknownKeys = true }
    val element = json.parseToJsonElement(content)
    return encodeMessage(parseJsonToMap(element))
}

/* ───────────────────────────────────────────── */
/* ───────────── internal builders ───────────── */
/* ───────────────────────────────────────────── */

fun buildBasePbContent(id: String, isGroupMsg: Boolean): JsonObject = buildJsonObject {
    putJsonObject("1") {
        if (isGroupMsg) {
            val gid = id.toLongOrNull()
                ?: error("id must be Long for group messages")
            putJsonObject("2") { put("1", JsonPrimitive(gid)) }
        } else {
            putJsonObject("1") { put("2", JsonPrimitive(id)) }
        }
    }
    putJsonObject("2") {
        put("1", JsonPrimitive(1))
        put("2", JsonPrimitive(0))
        put("3", JsonPrimitive(0))
    }
    putJsonObject("3") {        // message body
        putJsonObject("1") { put("2", buildJsonArray {}) }
    }
}

/* ───────────────────────────────────────────── */
/* ───────────── Protobuf encoding ───────────── */
/* ───────────────────────────────────────────── */

fun encodeMessage(obj: Map<Int, Any>): ByteArray =
    ByteArrayOutputStream().use { baos ->
        CodedOutputStream.newInstance(baos).apply {
            encodeMapToProtobuf(this, obj)
            flush()
        }
        baos.toByteArray()
    }

private fun encodeMapToProtobuf(
    output: CodedOutputStream,
    obj: Map<Int, Any>
) {
    obj.forEach { (tag, value) ->
        when (value) {
            is Int      -> output.writeInt32(tag, value)
            is Long     -> output.writeInt64(tag, value)
            is String   -> output.writeString(tag, value)
            is ByteArray -> {
                output.writeTag(tag,
                    com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                output.writeUInt32NoTag(value.size)
                output.writeRawBytes(value)
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val child = encodeMessage(value as Map<Int, Any>)
                output.writeTag(tag,
                    com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                output.writeUInt32NoTag(child.size)
                output.writeRawBytes(child)
            }
            is List<*> -> value.forEach { item ->
                when (item) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val child = encodeMessage(item as Map<Int, Any>)
                        output.writeTag(tag,
                            com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                        output.writeUInt32NoTag(child.size)
                        output.writeRawBytes(child)
                    }
                    is Int    -> output.writeInt32(tag, item)
                    is Long   -> output.writeInt64(tag, item)
                    is String -> output.writeString(tag, item)
                    is ByteArray -> {
                        output.writeTag(tag,
                            com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                        output.writeUInt32NoTag(item.size)
                        output.writeRawBytes(item)
                    }
                    else -> error("Unsupported list item: ${item?.javaClass}")
                }
            }
            else -> error("Unsupported type: ${value.javaClass}")
        }
    }
}

/* ───────────────────────────────────────────── */
/* ─────────────── JSON → Map<Int,Any> ───────── */
/* ───────────────────────────────────────────── */

fun parseJsonToMap(
    jsonElement: JsonElement,
    path: List<String> = emptyList()
): Map<Int, Any> {
    val out = mutableMapOf<Int, Any>()

    when (jsonElement) {
        is JsonObject -> {
            for ((k, v) in jsonElement) {
                val intKey = k.toIntOrNull()
                    ?: error("Key is not a valid int: $k")

                val here   = path + k
                val tag    = if (here == listOf("3","1","2")) 2 else intKey

                out[tag] = when (v) {
                    is JsonObject -> parseJsonToMap(v, here)
                    is JsonArray  -> v.mapIndexed { idx, el ->
                        when (el) {
                            is JsonObject, is JsonArray ->
                                parseJsonToMap(el, here + (idx + 1).toString())
                            is JsonPrimitive            -> primitiveOf(el)
                            else -> error("Bad array element: $el")
                        }
                    }
                    is JsonPrimitive -> primitiveOrHex(here, v)
                    else -> error("Unsupported JSON element: $v")
                }
            }
        }

        is JsonArray -> {
            jsonElement.forEachIndexed { idx, el ->
                val value = when (el) {
                    is JsonObject, is JsonArray ->
                        parseJsonToMap(el, path + (idx + 1).toString())
                    is JsonPrimitive -> primitiveOf(el)
                    else -> error("Bad array element: $el")
                }
                out[idx + 1] = value
            }
        }

        is JsonPrimitive ->
            return mapOf(1 to primitiveOf(jsonElement))

        else -> error("Unsupported JSON element: $jsonElement")
    }
    return out
}

/* ───────────────────────────────────────────── */
/* ──────────────── utilities ────────────────── */
/* ───────────────────────────────────────────── */

private fun primitiveOf(p: JsonPrimitive): Any =
    when {
        p.isString       -> p.content
        p.intOrNull  != null -> p.int
        p.longOrNull != null -> p.long
        else                -> p.content
    }

/* hex-string auto-convert on designated paths */
private fun primitiveOrHex(path: List<String>, p: JsonPrimitive): Any {
    if (!p.isString) return primitiveOf(p)
    val s = p.content

    return when {
        s.startsWith("hex->") && isHexString(s.removePrefix("hex->")) ->
            hexStringToByteArray(s.removePrefix("hex->"))
        path.takeLast(2) == listOf("5","2") && isHexString(s) ->
            hexStringToByteArray(s)
        else -> s
    }
}

private fun isHexString(s: String): Boolean =
    s.length % 2 == 0 && s.matches(Regex("^[0-9a-fA-F]+$"))

private fun hexStringToByteArray(s: String): ByteArray {
    val data = ByteArray(s.length / 2)
    for (i in s.indices step 2) {
        val hi = Character.digit(s[i],     16)
        val lo = Character.digit(s[i + 1], 16)
        require(hi >= 0 && lo >= 0) { "Invalid hex at $i" }
        data[i / 2] = ((hi shl 4) + lo).toByte()
    }
    return data
}
