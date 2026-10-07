package dev.thorpilot
import org.junit.Test
import org.junit.Assert.*
class ThorDownloadProtocolTest {
    @Test fun fullResponseAndValidResume() {
        ThorDownloadProtocol.validateRange(200,null,0,100)
        ThorDownloadProtocol.validateRange(206,"bytes 30-99/100",30,100)
    }
    @Test fun neverAppendFullResponseOrWrongRange() {
        listOf(Triple(200,null,30L),Triple(206,"bytes 0-99/100",30L),Triple(206,"bytes 30-100/101",30L),Triple(302,null,0L)).forEach { (code,range,start) ->
            assertThrows(IllegalArgumentException::class.java) { ThorDownloadProtocol.validateRange(code,range,start,100) }
        }
    }
    @Test fun missingHashAndInvalidSizeCannotAuthorizeDownload() {
        val valid = ThorDownloadEntry("1", "Game", "nds", "Game.nds", 100, "a".repeat(64))
        listOf(valid.copy(sha256=""),valid.copy(sha256="a".repeat(63)),valid.copy(sizeBytes=0),valid.copy(id="")).forEach { e ->
            assertThrows(IllegalArgumentException::class.java) { e.destination() }
        }
    }
    @Test fun truncatedOrOversizedRangeCannotBeAppended() {
        listOf("bytes 30-98/100", "bytes 30-100/100", "bytes 31-99/100", "bytes 30-99/101").forEach { range ->
            assertThrows(IllegalArgumentException::class.java) { ThorDownloadProtocol.validateRange(206,range,30,100) }
        }
    }
    @Test fun safeRouting() {
        fun entry(platform:String,name:String)=ThorDownloadEntry("1","Game",platform,name,100,"a".repeat(64))
        assertEquals("n3ds/Game.cia",entry("3ds","Game.cia").destination())
        assertEquals("psp/Game.iso",entry("psp","Game.iso").destination())
        listOf(entry("nds","SoulSilver.sav"),entry("switch","../Game.nsp"),entry("n3ds","Game.3ds"),entry("psp","Game.zip")).forEach { e ->
            assertThrows(IllegalArgumentException::class.java) { e.destination() }
        }
    }
}
