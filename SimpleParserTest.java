package com.github.johnmcguinness.simpleparser;

import io.vavr.Tuple;
import io.vavr.control.Either;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static com.github.johnmcguinness.simpleparser.Parser.consumeBase;
import static com.github.johnmcguinness.simpleparser.Parser.keep;
import static com.github.johnmcguinness.simpleparser.Parser.succeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParserTest {

    @Test
    public void succeedTest() {

        int expected = 3;
        final Either<List<DeadEnd<Void, Void>>, Integer> result = Parser.run(succeed(expected), "abc");

        assertTrue(result.isRight());
        assertEquals(expected, result.get());
    }

    @Test
    public void problemTest() {

        final String msg = "There was an error";
        final Either<List<DeadEnd<Void, String>>, Integer> result = Parser.run(Parser.problem(msg), "abc");

        assertTrue(result.isLeft());

        final List<DeadEnd<Void, String>> deadEnds = result.getLeft();

        assertNotNull(deadEnds);
        assertEquals(1, deadEnds.size());

        final DeadEnd<Void, String> deadend = deadEnds.get(0);

        assertEquals(1, deadend.row());
        assertEquals(1, deadend.col());
        assertEquals(msg, deadend.problem());
        assertTrue(deadend.contextStack().isEmpty());
    }

    @Test
    public void mapTest() {

        final String expected = "3";
        final Parser<Void, String, Integer> m = Parser.map(Integer::valueOf, succeed(expected));

        final Either<List<DeadEnd<Void, String>>, Integer> result = Parser.run(m, "");
        // Parser.succeed(expected).map(Integer::valueOf).run("");

        assertTrue(result.isRight());

        assertEquals(3, result.get());
    }

    @Test
    public void keepTest() {

//        record Demo2(String value1, String value2) {}
//        Parser<Void, Void, BiFunction<String, String,Demo2>> Demo2 = succeed(Demo2::new);

        record Demo1(String value) { }

        Parser<Void, Void, Integer> p = keep(succeed(Integer::valueOf), succeed("1"));

        Parser<Void, Void, Function<String, Demo1>> Demo1 = succeed(Demo1::new);


        Parser<Void, Void, Demo1> p1 = keep(Demo1, succeed("1"));

//        Parser<Void, Void, Demo2> p2 = keep(Demo2, succeed("1"));

        /*
            keep(
                succeed(Demo::new),
                succeed("1")
            );
        */
        final Either<List<DeadEnd<Void, Void>>, Integer> result = Parser.run(p, "");

        assertTrue(result.isRight());
        assertEquals(1, result.get());
    }

    @Test
    public void andThenTest() {

        // Parser.succeed("1").andThen(callback).run("2");
        final Function<String, Parser<Void, Void, Integer>> callback = s -> Parser.int_(null, null);
        final Parser<Void, Void, Integer> parser = Parser.andThen(callback, Parser.succeed("1"));
        final Either<List<DeadEnd<Void, Void>>, Integer> result = Parser.run(parser, "2");

        assertTrue(result.isRight());
        assertEquals(2, result.get());
    }

    @Test
    public void lazyTest() {

        Parser<Void, String, Integer> p = Parser.lazy(() -> Parser.int_("expecting_message", "invalid_message"));

        Either<List<DeadEnd<Void, String>>, Integer> result = Parser.run(p, "12");

        assertTrue(result.isRight());
        assertEquals(12, result.get());
    }

    @Test
    public void intTest_Success_IntOnly() {

        final int intNumber = 15;

        final Either<List<DeadEnd<Void, String>>, Integer> result
            = Parser.run(Parser.int_("expecting an int", "invalid int"), String.valueOf(intNumber));

        assertTrue(result.isRight());
        assertEquals(intNumber, result.get());
    }

    @Test
    public void intTest_Success_FollowedByAlpha() {

        final int intNumber = 15;

        final Either<List<DeadEnd<Void, String>>, Integer> result
            = Parser.run(Parser.int_("expecting an int", "invalid int"), intNumber + "ab");

        assertTrue(result.isRight());
        assertEquals(intNumber, result.get());
    }

    @Test
    public void intTest_Invalid_Float() {

        final int intNumber = 15;
        final double doubleNumber = intNumber + 0.7;

        final Either<List<DeadEnd<Void, String>>, Integer> result
            = Parser.run(Parser.int_("expecting an int", "invalid int"), String.valueOf(doubleNumber));

        assertTrue(result.isLeft());

        List<DeadEnd<Void, String>> deadEnds = result.getLeft();

        assertEquals(1, deadEnds.size());
        assertEquals(new DeadEnd<>(1, 1, "invalid int", List.of()), deadEnds.get(0));
    }

    @Test
    public void floatTest_Success() {

        final float doubleNumber = 15.7f;

        final Either<List<DeadEnd<Void, String>>, Float> result
            = Parser.run(Parser.float_("expecting an float", "invalid float"), String.valueOf(doubleNumber));

        assertTrue(result.isRight());
        assertEquals(doubleNumber, result.get());
    }

    @Test
    public void floatTest_Success_AsInteger() {

        final int doubleNumber = 15;

        final Either<List<DeadEnd<Void, String>>, Float> result
            = Parser.run(Parser.float_("expecting an float", "invalid float"), String.valueOf(doubleNumber));

        assertTrue(result.isRight());
        assertEquals(doubleNumber, result.get());
    }

    @Test
    public void spacesTest() {

        final String newLine = "\n";
        final String carriageReturn = "\r";
        final String space = " ";

        assertTrue(Parser.run(Parser.spaces(), newLine).isRight());
        assertTrue(Parser.run(Parser.spaces(), carriageReturn).isRight());
        assertTrue(Parser.run(Parser.spaces(), space).isRight());
    }

    @Test
    public void consumeBaseTest_Base10() {

        final int decimal = 10;
        final int initialOffset = 0;

        final String zero = "0";
        final String one = "1";
        final String twelve = "12";
        final String alpha1 = "abc";
        final String alpha2 = "a1";
        final String numalpha = "1a";

        assertEquals(Tuple.of(1, 0), consumeBase(decimal, initialOffset, zero));
        assertEquals(Tuple.of(1, 1), consumeBase(decimal, initialOffset, one));
        assertEquals(Tuple.of(1, 1), consumeBase(decimal, initialOffset, numalpha));
        assertEquals(Tuple.of(2, 12), consumeBase(decimal, initialOffset, twelve));

        assertEquals(Tuple.of(0, 0), consumeBase(decimal, initialOffset, alpha1));
        assertEquals(Tuple.of(2, 1), consumeBase(decimal, 1, alpha2));
    }

    @Test
    public void consumeBaseTest_Base2() {

        final int binary = 2;
        final int initialOffset = 0;

        final String zero = "0";
        final String one = "1";
        final String invalidDigit = "2";
        final String twelve = "1100";
        final String alpha = "abc";
        final String alphanum = "a1";
        final String numalpha = "1a";

        assertEquals(Tuple.of(1, 0), consumeBase(binary, initialOffset, zero));
        assertEquals(Tuple.of(1, 1), consumeBase(binary, initialOffset, one));
        assertEquals(Tuple.of(1, 1), consumeBase(binary, initialOffset, numalpha));
        assertEquals(Tuple.of(4, 12), consumeBase(binary, initialOffset, twelve));

        assertEquals(Tuple.of(0, 0), consumeBase(binary, initialOffset, invalidDigit));
        assertEquals(Tuple.of(0, 0), consumeBase(binary, initialOffset, alpha));
        assertEquals(Tuple.of(2, 1), consumeBase(binary, 1, alphanum));
    }

    @Test
    public void consumeBaseTest_Base8() {

        final int octal = 8;
        final int initialOffset = 0;

        final String zero = "0";
        final String one = "1";
        final String invalidDigit = "8";
        final String twelve = "14";
        final String alpha = "abc";
        final String alphanum = "a1";
        final String numalpha = "6a";

        assertEquals(Tuple.of(1, 0), consumeBase(octal, initialOffset, zero));
        assertEquals(Tuple.of(1, 1), consumeBase(octal, initialOffset, one));
        assertEquals(Tuple.of(1, 6), consumeBase(octal, initialOffset, numalpha));
        assertEquals(Tuple.of(2, 12), consumeBase(octal, initialOffset, twelve));

        assertEquals(Tuple.of(0, 0), consumeBase(octal, initialOffset, invalidDigit));
        assertEquals(Tuple.of(0, 0), consumeBase(octal, initialOffset, alpha));
        assertEquals(Tuple.of(2, 1), consumeBase(octal, 1, alphanum));
    }
}
