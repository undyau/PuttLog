package com.undy.puttrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun `parses digit distance with hit`() {
        val result = CommandParser.parse("20 hit")
        assertEquals(ParsedCommand.RecordPutt(20.0, true), result)
    }

    @Test
    fun `parses digit distance with miss`() {
        val result = CommandParser.parse("35 miss")
        assertEquals(ParsedCommand.RecordPutt(35.0, false), result)
    }

    @Test
    fun `is case insensitive`() {
        val result = CommandParser.parse("20 HIT")
        assertEquals(ParsedCommand.RecordPutt(20.0, true), result)
    }

    @Test
    fun `parses spoken number word with hit`() {
        val result = CommandParser.parse("twenty hit")
        assertEquals(ParsedCommand.RecordPutt(20.0, true), result)
    }

    @Test
    fun `parses compound spoken number with miss`() {
        val result = CommandParser.parse("twenty five miss")
        assertEquals(ParsedCommand.RecordPutt(25.0, false), result)
    }

    @Test
    fun `parses teen number word`() {
        val result = CommandParser.parse("fifteen made")
        assertEquals(ParsedCommand.RecordPutt(15.0, true), result)
    }

    @Test
    fun `recognizes synonyms for hit and miss`() {
        assertEquals(ParsedCommand.RecordPutt(10.0, true), CommandParser.parse("10 make"))
        assertEquals(ParsedCommand.RecordPutt(10.0, true), CommandParser.parse("10 in"))
        assertEquals(ParsedCommand.RecordPutt(10.0, false), CommandParser.parse("10 missed"))
        assertEquals(ParsedCommand.RecordPutt(10.0, false), CommandParser.parse("10 out"))
    }

    @Test
    fun `bare hit word repeats previous distance`() {
        assertEquals(ParsedCommand.RepeatDistance(true), CommandParser.parse("hit"))
        assertEquals(ParsedCommand.RepeatDistance(true), CommandParser.parse("made"))
    }

    @Test
    fun `bare miss word repeats previous distance`() {
        assertEquals(ParsedCommand.RepeatDistance(false), CommandParser.parse("miss"))
        assertEquals(ParsedCommand.RepeatDistance(false), CommandParser.parse("missed"))
    }

    @Test
    fun `parses stop command`() {
        assertEquals(ParsedCommand.Stop, CommandParser.parse("stop"))
        assertEquals(ParsedCommand.Stop, CommandParser.parse("Stop"))
    }

    @Test
    fun `unrecognized when no result word`() {
        val result = CommandParser.parse("20 feet")
        assertTrue(result is ParsedCommand.Unrecognized)
    }

    @Test
    fun `unrecognized when no number and no result word`() {
        val result = CommandParser.parse("hello there")
        assertTrue(result is ParsedCommand.Unrecognized)
    }

    @Test
    fun `unrecognized for empty input`() {
        val result = CommandParser.parse("   ")
        assertTrue(result is ParsedCommand.Unrecognized)
    }

    @Test
    fun `parseBest picks first candidate that parses`() {
        val result = CommandParser.parseBest(listOf("it", "20 make", "20 miss"))
        assertEquals(ParsedCommand.RecordPutt(20.0, true), result)
    }

    @Test
    fun `parseBest falls back to top candidate when none parse`() {
        val result = CommandParser.parseBest(listOf("gibberish", "more gibberish"))
        assertEquals(ParsedCommand.Unrecognized("gibberish"), result)
    }

    @Test
    fun `parseBest is unrecognized for empty candidate list`() {
        val result = CommandParser.parseBest(emptyList())
        assertTrue(result is ParsedCommand.Unrecognized)
    }
}
