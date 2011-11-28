/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.service.functions;

import com.akiban.server.aggregation.Aggregator;
import com.akiban.server.aggregation.AggregatorFactory;
import com.akiban.server.error.AkibanInternalException;
import com.akiban.server.expression.EnvironmentExpressionFactory;
import com.akiban.server.expression.EnvironmentExpressionSetting;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.expression.std.ExpressionTypes;
import com.akiban.server.types.AkType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class FunctionsRegistryImplTest {
    
    @Test
    public void findAggregatorFactory() {
        FunctionsRegistryImpl registry = registry(Good.class);
        assertEquals(expectedAggregatorFactories(), registry.getAllAggregators());
        assertEquals("afoo", AGGREGATOR_FACTORY, registry.get("afoo", AkType.LONG));
        assertEquals("AFOO", AGGREGATOR_FACTORY, registry.get("AFOO", AkType.LONG));
    }

    @Test
    public void findExpressionComposer() {
        FunctionsRegistryImpl registry = registry(Good.class);
        assertEquals(expectedExpressionFactories(), registry.getAllComposers());
        assertEquals("foo", GOOD_EXPRESSION_COMPOSER, registry.composer("foo"));
        assertEquals("FOO", GOOD_EXPRESSION_COMPOSER, registry.composer("FOO"));
    }

    @Test
    public void findEnvironmentFactory() {
        FunctionsRegistryImpl registry = registry(Good.class);
        assertEquals(expectedEnvironmentFactories(), registry.getAllEnvironments());
        assertEquals("efoo", ENVIRONMENT_FACTORY, registry.environment("efoo"));
        assertEquals("EFOO", ENVIRONMENT_FACTORY, registry.environment("EFOO"));
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void scalarWrongType() {
        registry(ScalarWrongType.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void scalarNotPublic() {
        registry(ScalarNotPublic.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void scalarNotFinal() {
        registry(ScalarNotFinal.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void scalarNotStatic() {
        registry(ScalarNotStatic.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void duplicateScalarAggregate() {
        registry(ScalarDuplicate.class, AggDuplicate.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void aggregateNotStatic() {
        registry(AggNotStatic.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void aggregateNotPublic() {
        registry(AggNotPublic.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void aggregateWrongRetValue() {
        registry(AggWrongReturnValue.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void aggregateWrongArgs() {
        registry(AggWrongArgs.class);
    }

    @Test(expected = AkibanInternalException.class)
    public void aggregateThrowsException() {
        registry(AggThrowsException.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void environmentWrongType() {
        registry(EnvironmentWrongType.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void environmentNotPublic() {
        registry(EnvironmentNotPublic.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void environmentNotFinal() {
        registry(EnvironmentNotFinal.class);
    }

    @Test(expected = FunctionsRegistryImpl.FunctionsRegistryException.class)
    public void environmentNotStatic() {
        registry(EnvironmentNotStatic.class);
    }

    // use in this class

    private static FunctionsRegistryImpl registry(Class<?>... classes) {
        return new FunctionsRegistryImpl(new InternalClassFinder(classes));
    }

    private static AggregatorFactory aggregatorFactoryMethod(String name, AkType type) {
        assert name != null;
        return type == AkType.LONG ? AGGREGATOR_FACTORY : null;
    }

    public static final ExpressionComposer GOOD_EXPRESSION_COMPOSER = new ExpressionComposer() {
        @Override
        public Expression compose(List<? extends Expression> arguments) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void argumentTypes(List<AkType> argumentTypes) {
            throw new UnsupportedOperationException();
        }
        @Override
        public ExpressionType composeType(List<? extends ExpressionType> argumentTypes) {
            throw new UnsupportedOperationException();
        }
    };

    static Map<String, Map<AkType, AggregatorFactory>> expectedAggregatorFactories() {
        Map<String,Map<AkType,AggregatorFactory>> expected = new HashMap<String, Map<AkType, AggregatorFactory>>();
        Map<AkType,AggregatorFactory> expectedInner = new EnumMap<AkType, AggregatorFactory>(AkType.class);
        expectedInner.put(AkType.LONG, AGGREGATOR_FACTORY);
        expected.put("afoo", expectedInner);
        return expected;
    }

    static Map<String,ExpressionComposer> expectedExpressionFactories() {
        return Collections.singletonMap("foo", GOOD_EXPRESSION_COMPOSER);
    }

    static Map<String,EnvironmentExpressionFactory> expectedEnvironmentFactories() {
        return Collections.singletonMap("efoo", ENVIRONMENT_FACTORY);
    }

    // class state

    private static final AggregatorFactory AGGREGATOR_FACTORY = new AggregatorFactory() {
        @Override
        public Aggregator get() {
            throw new UnsupportedOperationException();
        }
        @Override
        public AkType outputType() {
            return AkType.NULL;
        }
    };

    private static final EnvironmentExpressionFactory ENVIRONMENT_FACTORY = new EnvironmentExpressionFactory() {
        @Override
        public EnvironmentExpressionSetting environmentSetting() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression get(int bindingPosition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExpressionType getType() {
            return ExpressionTypes.NULL;
        }
    };

    // nested classes

    private static class InternalClassFinder implements FunctionsClassFinder {
        private InternalClassFinder(Class<?>... classes) {
            this.classes = new HashSet<Class<?>>(Arrays.asList(classes));
        }

        @Override
        public Set<Class<?>> findClasses() {
            return classes;
        }

        private final Set<Class<?>> classes;
    }

    // good example

    public static class Good {
        @Aggregate("AFOO") // note, should be converted to lowercase
        public static AggregatorFactory get(String name, AkType type) {
            return aggregatorFactoryMethod(name, type);
        }

        @Scalar("FOO") @SuppressWarnings("unused") // note, should be converted to lowercase
        public static final ExpressionComposer COMPOSER = GOOD_EXPRESSION_COMPOSER;

        @EnvironmentValue("EFOO") @SuppressWarnings("unused") // note, should be converted to lowercase
        public static final EnvironmentExpressionFactory ENVIRONMENT = ENVIRONMENT_FACTORY;
    }

    // bad scalars

    public static class ScalarWrongType {
        @Scalar("blah") @SuppressWarnings("unused")
        public static final Boolean WRONG = false;
    }

    public static class ScalarNotPublic {
        @Scalar("blah") @SuppressWarnings("unused")
        static final ExpressionComposer COMPOSER = GOOD_EXPRESSION_COMPOSER;
    }

    public static class ScalarNotFinal {
        @Scalar("blah") @SuppressWarnings("unused")
        public static ExpressionComposer COMPOSER = GOOD_EXPRESSION_COMPOSER;
    }

    public static class ScalarNotStatic {
        @Scalar("blah") @SuppressWarnings("unused")
        public final ExpressionComposer COMPOSER = GOOD_EXPRESSION_COMPOSER;
    }

    public static class ScalarDuplicate {
        @Scalar("foo") @SuppressWarnings("unused")
        public static final ExpressionComposer COMPOSER_A = GOOD_EXPRESSION_COMPOSER;
    }

    // bad aggregates

    public static class AggNotStatic {
        @Aggregate("foo") @SuppressWarnings("unused")
        public AggregatorFactory get(String name, AkType type) {
            return null;
        }
    }

    public static class AggNotPublic {
        @Aggregate("foo") @SuppressWarnings("unused")
        static AggregatorFactory get(String name, AkType type) {
            return null;
        }
    }

    public static class AggWrongReturnValue {
        @Aggregate("foo") @SuppressWarnings("unused")
        public Boolean get(String name, AkType type) {
            return null;
        }
    }

    public static class AggWrongArgs {
        @Aggregate("foo") @SuppressWarnings("unused")
        public Boolean get(String name, AkType type, Integer i) {
            return null;
        }
    }

    public static class AggDuplicate {
        @Aggregate("foo") @SuppressWarnings("unused")
        public static AggregatorFactory getA(String name, AkType type) {
            return null;
        }
    }
    public static class AggThrowsException {
        @Aggregate("foo") @SuppressWarnings("unused")
        public static AggregatorFactory get(String name, AkType type) {
            throw new UnsupportedOperationException();
        }
    }

    // bad enviroments

    public static class EnvironmentWrongType {
        @EnvironmentValue("blah") @SuppressWarnings("unused")
        public static final Boolean WRONG = false;
    }

    public static class EnvironmentNotPublic {
        @EnvironmentValue("blah") @SuppressWarnings("unused")
        static final EnvironmentExpressionFactory COMPOSER = ENVIRONMENT_FACTORY;
    }

    public static class EnvironmentNotFinal {
        @EnvironmentValue("blah") @SuppressWarnings("unused")
        public static EnvironmentExpressionFactory COMPOSER = ENVIRONMENT_FACTORY;
    }

    public static class EnvironmentNotStatic {
        @EnvironmentValue("blah") @SuppressWarnings("unused")
        public final EnvironmentExpressionFactory COMPOSER = ENVIRONMENT_FACTORY;
    }

}