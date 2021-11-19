package com.github.johnmcguinness.simpleparser;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.control.Either;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    public static <C, X, T1, T2> Parser<C, X, T2> andThen(Function<T1, Parser<C, X, T2>> callback, Parser<C, X, T1> parser) {

        return new Parser<>(s0 -> {

            final PStep<C, X, T1> resultA = parser.parse.apply(s0);

            if(resultA instanceof Bad<C, X, T1> badA ) {
                return new Bad<>(badA.progress(), badA.bag());
            }
            else {
                final Good<C, X, T1> goodA = (Good<C, X, T1>) resultA;
                final Parser<C, X, T2> parserB = callback.apply(goodA.value());
                final PStep<C, X, T2> resultB = parserB.parse.apply(goodA.state());

                if(resultB instanceof Bad<C, X, T2> badB ) {
                    return new Bad<>(goodA.progress() || badB.progress(), badB.bag());
                }
                else {
                    final Good<C, X, T2> goodB = (Good<C, X, T2>) resultB;
                    return new Good<>(goodA.progress() || goodB.progress(), goodB.value(), goodB.state());
                }
            }
        });
    }

    public static <C, X, T> Parser<C, X, T> lazy(Supplier<Parser<C, X, T>> thunk) {
        return new Parser<>(s -> thunk.get().parse.apply(s));
    }

    public static <C, X> Parser<C, X, Void> token(final Token<X> token) {

        final boolean progress = !token.string().isEmpty();

        return new Parser<>(s -> {

            final Tuple3<Integer, Integer, Integer> triple = isSubString(token.string(), s.offset(), s.row(), s.column(), s.source());

            final int newOffset = triple._1;
            final int newRow = triple._2;
            final int newCol = triple._3;

            if(newOffset == -1) {
                return new Bad<>(false, Bag.fromState(s, token.expecting()));
            }
            else {
                return new Good<>(progress, null, new State<>(s.source(), newOffset, s.indent(), s.context(), newRow, newCol));
            }
        });
    }

    public static <C, X> Parser<C, X, Void> symbol(final Token<X> token) {
        return token(token);
    }

    public static <C, X> Parser<C, X, Void> end(X expecting) {

        return new Parser<>(s -> {

            if(s.source().length() == s.offset()) {
                return new Good<>(false, null, s);
            }
            else {
                return new Bad<>(false, Bag.fromState(s, expecting));
            }
        });
    }

    public static <C, X> Parser<C, X, Integer> int_( X expecting, X invalid) {
        return number(new NumberConfig<>(
            Either.right(Function.identity()),
            Either.left(invalid),
            Either.left(invalid),
            Either.left(invalid),
            Either.left(invalid),
            invalid,
            expecting));
    }

    public static <C, X> Parser<C, X, Float> float_( X expecting, X invalid) {
        return number(new NumberConfig<>(
            Either.right(Float::valueOf),
            Either.left(invalid),
            Either.left(invalid),
            Either.left(invalid),
            Either.right(Function.identity()),
            invalid,
            expecting));
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
                    return finaliseFloat(config.invalid(), config.expecting(), config.int_(), config.float_(), Tuple.of(zeroOffset, 0), s);
                }
            }
            else {
                return finaliseFloat(config.invalid(), config.expecting(), config.int_(), config.float_(), consumeBase(10, s.offset(), s.source()), s);
            }
        });
    }

    public static <C, X, T> Parser<C, X, T> inContext(final C context, final Parser<C, X, T> parser) {

        return new Parser<>(s0 -> {

            final List<Located<C>> newContextStack = new ArrayList<>(s0.context());
            if(newContextStack.isEmpty()) {
                newContextStack.add(new Located<>(s0.row(), s0.column(), context));
            }
            else {
                newContextStack.set(0, new Located<>(s0.row(), s0.column(), context));
            }

            final PStep<C, X, T> result =  parser.parse.apply(changeContext(newContextStack, s0));

            if(result instanceof Good<C, X, T> good) {
                return new Good<>(good.progress(), good.value(), changeContext(s0.context(), good.state()));
            }
            else {
                return result;
            }
        });
    }

    public static <C, X> Parser<C, X, Void> keyword(final Token<X> token) {

        final String kwd = token.string();
        final X expecting = token.expecting();

        final boolean progress = !kwd.isEmpty();

        return new Parser<>(s -> {

            final Tuple3<Integer, Integer, Integer> triple = isSubString(token.string(), s.offset(), s.row(), s.column(), s.source());

            final int newOffset = triple._1;
            final int newRow = triple._2;
            final int newCol = triple._3;

            if(newOffset == -1 || 0<= isSubChar(c -> Character.isLetterOrDigit(c) || c == '_', newOffset, s.source())) {
                return new Bad<>(false, Bag.fromState(s, expecting));
            }
            else {
                return new Good<>(progress, null , new State<>(s.source(), newOffset, s.indent(), s.context(), newRow, newCol));
            }
        });
    }

    public static <C, X> Parser<C, X, String> variable(VariableConfig<X> varConfig) {

        return new Parser<>(s -> {

            final int firstOffset = isSubChar(varConfig.start(), s.offset(), s.source());

            if(firstOffset == -1) {
                return new Bad<>(false, Bag.fromState(s, varConfig.expecting()));
            }
            else {

                final State<C> s1 = firstOffset == -2
                    ? varHelp(varConfig.inner(), s.offset() + 1, s.row() + 1, 1, s.source(), s.indent(), s.context())
                    : varHelp(varConfig.inner(), firstOffset, s.row(), s.column() + 1, s.source(), s.indent(), s.context());

                final String name = s.source().substring(s.offset(), s1.offset());

                if(varConfig.reserved().contains(name)) {
                    return new Bad<>(false, Bag.fromState(s, varConfig.expecting()));
                }
                else {
                    return new Good<>(true, name, s1);
                }
            }
        });
    }

    private static <C> State<C> varHelp(Predicate<Character> isGood, int offset, int row, int column, String source, int indent, List<Located<C>> context) {

        final int newOffset = isSubChar(isGood, offset, source);

        if(newOffset == -1) {
            return new State<>(source, offset, indent, context, row, column);
        }
        else if(newOffset == -2) {
            return varHelp(isGood, offset + 1, row + 1, 1, source, indent, context);
        }
        else {
            return varHelp(isGood, newOffset, row, column + 1, source, indent, context);
        }
    }

    private static <C> State<C> changeContext(final List<Located<C>> newContext, State<C> s) {
        return new State<>(s.source(), s.offset(), s.indent(), newContext, s.row(), s.column());
    }

    private static boolean isAsciiCode(int code, int offset, String string) {
        return offset < string.length() && string.codePointAt(offset) == code;
    }

    static Tuple2<Integer, Integer> consumeBase(int base, int offset, String string) {

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

        for(; offset < string.length(); offset++) {

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
        }

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
        final int floatOffset = consumeDotAndExp(intOffset, s.source());

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

                try {
                    final Float value = Float.valueOf(s.source().substring(s.offset(), floatOffset));

                    return new Good<>(true, toValue.apply(value), bumpOffset(floatOffset, s));
                }
                catch (NumberFormatException e) {

                }
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

    private static int consumeDotAndExp(int offset, String source) {
        if(isAsciiCode(0x2E, offset, source)) {
            return consumeExp(chompBase10((offset + 1), source), source);
        }
        else {
            return consumeExp(offset, source);
        }
    }

    private static int consumeExp(int offset, String source) {
        if(isAsciiCode(0x65, offset, source) || isAsciiCode(0x45, offset, source)) {

            int eOffset = offset + 1;
            int expOffset = (isAsciiCode(0x2B, eOffset, source) || isAsciiCode(0x2D, eOffset, source)) ? eOffset + 1 : eOffset;
            int newOffset = chompBase10(expOffset, source);

            if(expOffset == newOffset) {
                return -1 * newOffset;
            }
            else {
                return newOffset;
            }
        }
        else {
            return offset;
        }
    }

    private static int chompBase10(int offset, String string) {

        for(; offset < string.length(); offset++) {
            int code = string.codePointAt(offset);

            if(code < 0x30 || 0x39 < code) {
                return offset;
            }
        }

        return offset;
    }

    private static <C, X> Parser<C, X, Void> chompWhile(Predicate<Character> isGood) {
        return new Parser<>(s -> chompWhileHelp(isGood, s.offset(), s.row(), s.column(), s));
    }

    private static <C, X> PStep<C, X, Void> chompWhileHelp(Predicate<Character> isGood, int offset, int row, int column, State<C> s0) {

        final int newOffset = isSubChar(isGood, offset, s0.source());

        if(newOffset == -1) {
            return new Good<>(s0.offset() < offset, null, new State<>(s0.source(), offset, s0.indent(), s0.context(), row, column));
        }
        else if(newOffset == -2) {
            return chompWhileHelp(isGood, offset + 1, row + 1, 1, s0);
        }
        else {
            return chompWhileHelp(isGood, newOffset, row, column + 1, s0);
        }
    }

    public static <C, X> Parser<C, X, Void> spaces() {
        return chompWhile(Character::isWhitespace);
    }

    private static int isSubChar(Predicate<Character> predicate, int offset, String string) {

        return string.length() <= offset
                ? -1
                : (string.codePointAt(offset) & 0xF800) == 0xD800
                    ? (predicate.test((char) string.codePointAt(offset))
                            ? offset + 2
                            : -1)
                    : (predicate.test((char) string.codePointAt(offset))
                            ? ((string.charAt(offset) == '\n') ? -2 : (offset + 1))
                            : -1);
    }

    static Tuple3<Integer, Integer, Integer> isSubString(final String shorterString, int offset, int row, int col, String longerString) {

        final String substring =  longerString.substring(offset, offset + shorterString.length());

        if(shorterString.equals(substring)) {

            final int newOffset = offset + substring.length();
            final int newRow = row + ((int) substring.lines().count() - 1);
            final int newColumn = ((substring.lastIndexOf('\n') == -1) ? col + substring.length() : substring.length() - substring.lastIndexOf('\n'));

            return Tuple.of(newOffset, newRow, newColumn);
        }
        else {
            return Tuple.of(-1, row, col);
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

record VariableConfig<X>(Predicate<Character> start, Predicate<Character> inner, Set<String> reserved, X expecting) {}

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

record Token<X>(String string, X expecting) {}

record Empty<C, X>() implements Bag<C, X> {}

record AddRight<C, X>(Bag<C, X> bag, DeadEnd<C, X> deadend) implements Bag<C, X> {}

record Append<C, X>(Bag<C, X> left, Bag<C, X> right) implements Bag<C, X> {}

sealed interface PStep<C, X, T> permits Good, Bad {}

record Good<C, X, T>(boolean progress, T value, State<C> state) implements PStep<C, X, T> {}

record Bad<C, X, T>(boolean progress, Bag<C, X> bag) implements PStep<C, X, T> {}
