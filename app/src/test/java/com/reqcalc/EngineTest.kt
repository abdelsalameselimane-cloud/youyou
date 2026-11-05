package com.reqcalc

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineTest {
    @Test fun specExample() {
        val e = Engine.parse("Transaction 001\nREQ: 1500\n\n10-20-30-40\n\nTransaction 002\nREQ: 2500\n\n5-15-25-35\n")
        assertEquals(listOf(100, 80), e.map { it.total })
        assertEquals(180, Engine.summarize(e).grand)
    }
    @Test fun missingNumbersNeverGuessed() {
        val e = Engine.parse("REQ: 2,000.00 DZD\n10-20-30\nREQ: 3,000.00 DZD\nCARD: 1\n")
        assertEquals(listOf(Status.VERIFY, Status.VERIFY), e.map { it.status })
        assertEquals(0, Engine.summarize(e).grand)
    }
    @Test fun realAtmBlock() {
        val t = " 5631 04/11/26 ATM: 01168492 RC -1\r04:44:43 WITHDRAWAL\rREQ:42,000.00 DZD PROC:42,000.00 DZ\r  21 -   0 -   0 -   0\rCARD: 6280\r\u001B[020t 03:49:27 NOTES PRESENTED 21,0,0,0\r" +
            " 5632 04/11/26 ATM\r04:45:35 INQUIRY\rREQ:0.00 DZD   PROC:DZD\rCARD: 1\r"
        val e = Engine.parse(t)
        assertEquals(21, e[0].total); assertEquals("5631", e[0].ref)
        assertEquals(Status.NO_DISPENSE, e[1].status)
    }
    @Test fun emptyAndEdit() {
        assertEquals(0, Engine.parse("").size)
        val e = Engine.parse("REQ: 1\n1-1-1\n")[0]
        assertEquals(10, Engine.edit(e, listOf(1, 2, 3, 4)).total)
    }
    @Test fun noteCodesDriveTranslations() {
        val e = Engine.parse("REQ: 5\n1-2-3\nREQ: 0.00 DZD\nREQ: 9\nCARD: 1\n")
        assertEquals(listOf("BAD", "NOCASH", "NOLINE"), e.map { it.code })
        assertEquals("1-2-3", e[0].arg)
        assertEquals("EDITED", Engine.edit(e[0], listOf(1, 2, 3, 4)).code)
    }
    @Test fun csvEscapesQuotes() {
        val e = Engine.parse("REQ: 1\n1-1-1-1\n")[0].copy(note = "say \"hi\"")
        assertEquals(true, Engine.toCsv(listOf(e)).contains("\"say \"\"hi\"\"\""))
    }
}
