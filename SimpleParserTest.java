package com.github.johnmcguinness.simpleparser;

import io.vavr.control.Either;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.github.johnmcguinness.simpleparser.Parser.keep;
import static com.github.johnmcguinness.simpleparser.Parser.succeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleParserTest {

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
}
