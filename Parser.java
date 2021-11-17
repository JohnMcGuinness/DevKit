package com.github.johnmcguinness.simpleparser;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Parser<C, X, T> {

    private final Function<State<C>, PStep<C, X, T>> parse;

    public Parser(final Function<State<C>, PStep<C, X, T>> parse) {
        this.parse = parse;
    }

    public static <C, X, T> Either<List<DeadEnd<C, X>>, T> run(Parser<C, X, T> parser, String source) {
        final Function<State<C>, PStep<C, X, T>> parse = parser.parse;

        PStep<C, X, T> pStep = parse.apply(new State<>(source, 0, 1, List.of(), 1, 1));

        if(pStep instanceof Good<C, X, T> good) {
            return Either.right(good.value());
        }
        else { // Must be an instance of bad
            final Bad<C, X, T> bad = (Bad<C, X, T>) pStep;

            return Either.left(Bag.bagToList(bad.bag(), io.vavr.collection.List.empty()).toJavaList());
        }
    }

    public static <C, X, T> Parser<C, X, T> succeed(T value) {
        return new Parser<>((s) -> new Good<>(false, value, s));
    }

    public static <C, X, T> Parser<C, X, T> problem(X problem) {
        return new Parser<>((State<C> s) -> new Bad<>(false, Bag.fromState(s, problem)));
    }

    public static <C, X, T, R> Parser<C, X, R> map(Function<T, R> f, Parser<C, X, T> parser) {

        return new Parser<>(s -> {

            final PStep<C, X, T> result = parser.parse.apply(s);

            if(result instanceof Good<C, X, T> good) {
                return new Good<>(good.progress(), f.apply(good.value()), good.state());
            }
            else { // Must be an instance of bad
                final Bad<C, X, T> bad = (Bad<C, X, T>) result;
                return new Bad<>(bad.progress(), bad.bag());
            }
        });
    }

    public static <C, X, T1, T2, R> Parser<C, X, R> map2(BiFunction<T1, T2, R> f, Parser<C, X, T1> parserA, Parser<C, X, T2> parserB) {
        return new Parser<>((s0) -> {

            final PStep<C, X, T1> resultA = parserA.parse.apply(s0);

            if (resultA instanceof Bad<C, X, T1> badA) {
                return new Bad<>(badA.progress(), badA.bag());
            } else { // Must be an instance of good
                final Good<C, X, T1> goodA = (Good<C, X, T1>) resultA;
                final PStep<C, X, T2> resultB = parserB.parse.apply(goodA.state());

                if (resultB instanceof Bad<C, X, T2> badB) {
                    return new Bad<>(goodA.progress() || badB.progress(), badB.bag());
                } else { // Must be an instance of good
                    final Good<C, X, T2> goodB = (Good<C, X, T2>) resultB;
                    return new Good<>(goodA.progress() || goodB.progress(), f.apply(goodA.value(), goodB.value()), goodB.state());
                }
            }
        });
    }

    public static <C, X, T, R> Parser<C, X, R> keep(Parser<C, X, Function<T, R>> parseFunc, Parser<C, X, T> parseArg) {
        return map2(Function::apply, parseFunc, parseArg);
    }

    public static <C, X, T> Parser<C, X, T> number(final NumberConfig<X, T> config ) {

        return new Parser<>(s -> {

            if(isAsciiCode(0x30, s.offset(), s.source())) {

                final int zeroOffset = s.offset() + 1;
                final int baseOffset = zeroOffset + 1;

                if(isAsciiCode(0x78, zeroOffset,  s.source())) {
                    return finaliseInt(config.invalid(), config.hex(), baseOffset, consumeBase16(baseOffset, s.source()), s);
                }
                else if(isAsciiCode(0x6F, zeroOffset,  s.source())) {
                    return finaliseInt(config.invalid(), config.octal(), baseOffset, consumeBase(8, baseOffset, s.source()), s);
                }
                else if(isAsciiCode(0x62, zeroOffset,  s.source())) {
                    return finaliseInt(config.invalid(), config.binary(), baseOffset, consumeBase(2, baseOffset, s.source()), s);
                }
                else {
                    return finaliseFloat();
                }
            }
            else {
                return finaliseFloat();
            }
        });
    }

    private static boolean isAsciiCode(int code, int offset, String string) {
        return string.codePointAt(offset) == code;
    }

    private static Tuple2<Integer, Integer> consumeBase(int base, int offset, String string) {

        int result = 0;
        for(; offset < string.length(); offset++) {
            int digit = string.charAt(offset) - 0x30;
            if(digit < 0  || base <= digit) {
                break;
            }
            result = base * result + digit;
        }

        return Tuple.of(offset, result);
    }

    private static Tuple2<Integer, Integer> consumeBase16(int offset, String string) {

        int total = 0;

        while(offset < string.length()) {

            final int code = string.charAt(offset);

            if(0x30 <= code && code <= 0x39) {
                total = 16 * total + code - 0x30;
            }
            else if(0x41 <= code && code <= 0x46) {
                total = 16 * total + code - 55;
            }
            else if (0x61 <= code && code <= 0x66) {
                total = 16 * total + code - 87;
            }
            else {
                break;
            }

            offset++;
        }
//
//        for(; offset < string.length(); offset++) {
//
//            int code = string.charAt(offset);
//
//            if(0x30 <= code && code <= 0x39) {
//                total = 16 * total + code - 0x30;
//            }
//            else if(0x41 <= code && code <= 0x46) {
//                total = 16 * total + code - 55;
//            }
//            else if (0x61 <= code && code <= 0x66) {
//                total = 16 * total + code - 87;
//            }
//            else {
//                break;
//            }
//        }

        return Tuple.of(offset, total);
    }

    private static <C, X, T> PStep<C, X, T> finaliseFloat(
            final X invalid,
            final X expecting,
            final Either<X, Function<Integer, T>> intSettings,
            final Either<X, Function<Float, T>> floatSettings,
            final Tuple2<Integer, Integer> intPair,
            final State<C> s)
    {
        final int intOffset = intPair._1;
//        final int floatOffset = consumeDotAndExp(intOffset, s.source());
        final int floatOffset = 0;

        if(floatOffset < 0) {
            return new Bad<>(true, Bag.fromInfo(s.row(), (s.column() - (floatOffset + s.offset())), invalid, s.context()));
        }
        else if(s.offset() == floatOffset) {
            return new Bad<>(false, Bag.fromState(s, expecting));
        }
        else if (intOffset == floatOffset) {
            return finaliseInt(invalid, intSettings, s.offset(), intPair, s);
        }
        else {
            if(floatSettings.isLeft()) {
                return new Bad<>(true, Bag.fromState(s, invalid));
            }
            else {
                final Function<Float, T> toValue = floatSettings.get();
                return null;
            }
        }
    }

    private static <C, X, T> PStep<C, X, T> finaliseInt(
            final X invalid,
            final Either<X, Function<Integer, T>> handler,
            final int startOffset,
            final Tuple2<Integer, Integer> tuple,
            final State<C> s)
    {
        if(handler.isLeft()) {
            return new Bad<>(true, Bag.fromState(s, handler.getLeft()));
        }
        else {
            final Function<Integer, T> toValue = handler.get();

            if(startOffset == tuple._1) {
                return new Bad((s.offset() < startOffset), Bag.fromState(s, invalid));
            }
            else {
                return new Good<>(true, toValue.apply(tuple._2), bumpOffset(tuple._1, s));
            }
        }
    }

    private static <C> State<C> bumpOffset(int newOffset, State<C> s) {
        return new State<>(s.source(), newOffset, s.indent(), s.context(), s.row(), s.column() + (newOffset - s.offset()));
    }
}

record NumberConfig<X, T>(
        Either<X, Function<Integer, T>> int_,
        Either<X, Function<Integer, T>> hex,
        Either<X, Function<Integer, T>> octal,
        Either<X, Function<Integer, T>> binary,
        Either<X, Function<Float, T>> float_,
        X invalid,
        X expecting) {}

record State<T>(String source, int offset, int indent, List<Located<T>> context, int row, int column) {}

record Located<T>(int row, int column, T context) {}

record DeadEnd<C, X>(int row, int col, X problem, List<Located<C>> contextStack) {}

// Bag
sealed interface Bag<C, X> permits Empty, AddRight, Append {

    static <C, X> Bag<C, X> fromState(final State<C> state, X x) {
        return new AddRight<>(new Empty<>(), new DeadEnd<>(state.row(), state.column(), x, state.context()));
    }

    static <C, X> Bag<C, X> fromInfo(final int row, final int col, final X x, final List<Located<C>> context) {
        return new AddRight<>(new Empty<>(), new DeadEnd<>(row, col, x, context));
    }

    static <C, X> io.vavr.collection.List<DeadEnd<C, X>> bagToList(final Bag<C, X> bag, final io.vavr.collection.List<DeadEnd<C, X>> list) {

        if(bag instanceof Empty) {
            return list;
        }
        else if(bag instanceof AddRight<C, X> addRight) {
            return bagToList(addRight.bag(), list.prepend(addRight.deadend()));
        }
        else {
            final Append<C, X> append = (Append<C, X>) bag;
            return bagToList(append.left(), bagToList(append.right(), list));
        }
    }
}

record Empty<C, X>() implements Bag<C, X> {}

record AddRight<C, X>(Bag<C, X> bag, DeadEnd<C, X> deadend) implements Bag<C, X> {}

record Append<C, X>(Bag<C, X> left, Bag<C, X> right) implements Bag<C, X> {}

sealed interface PStep<C, X, T> permits Good, Bad {}

record Good<C, X, T>(boolean progress, T value, State<C> state) implements PStep<C, X, T> {}

record Bad<C, X, T>(boolean progress, Bag<C, X> bag) implements PStep<C, X, T> {}
