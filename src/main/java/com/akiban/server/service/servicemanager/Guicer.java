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

package com.akiban.server.service.servicemanager;

import com.akiban.server.service.servicemanager.configuration.ServiceBinding;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.Exceptions;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public final class Guicer {
    // Guicer interface

    public Collection<Class<?>> directlyRequiredClasses() {
        return directlyRequiredClasses;
    }

    public void stopAllServices(ServiceLifecycleActions<?> withActions) {
        try {
            stopServices(withActions, null);
        } catch (Exception e) {
            throw new RuntimeException("while stopping services", e);
        }
    }

    public <T> T get(Class<T> serviceClass, ServiceLifecycleActions<?> withActions) {
        final T instance = _injector.getInstance(serviceClass);
        return startService(serviceClass, instance, withActions);
    }

    public boolean serviceIsStarted(Class<?> serviceClass) {
        return services.contains(serviceClass);
    }

    public boolean isRequired(Class<?> interfaceClass) {
        return directlyRequiredClasses.contains(interfaceClass);
    }

    /**
     * <p>Builds and returns a list of dependencies for an instance of the specified class. The list will include an
     * instance of the class itself, as well as any instances that root instance requires, directly or indirectly,
     * according to Guice constructor injection.</p>
     *
     * <p>The order of the resulting list is fully deterministic and guarantees that for any element N in the list,
     * if N depends on another element M, then M will also be in the list and will have an index greater than N's.
     * In other words, traversing the list in reverse order will guarantee that you see any class's dependency
     * before you see it.</p>
     *
     * <p>Each instance will appear exactly once; in other words, if there is an element N in the list,
     * there will be no element M such that {@code N == M} or such that
     * {@code N.getClass() == M.getClass()}</p>
     *
     * <p>More specifically, the order of the list is generated by doing a depth-first traversal of the dependency
     * graph, with each element's dependents visited in alphabetical order of their class names, and then removing
     * duplicates by doing a reverse traversal of the list. By that last point we mean that if N and M are duplicates,
     * and N.index > M.index, then we would keep N and discard M.</p>
     *
     * <p>This method returns a new list with each invocation; the caller is free to modify the returned list.</p>
     * @param rootClass the root class for which to get dependencies
     * @return a mutable list of dependency instances, including the instance specified by the root class, in an order
     * such that any element in the list always precedes all of its dependencies
     * @throws CircularDependencyException if a circular dependency is found
     */
    public List<?> dependenciesFor(Class<?> rootClass) {
        LinkedHashMap<Class<?>,Object> result = new LinkedHashMap<Class<?>,Object>(16, .75f, true);
        Deque<Object> dependents = new ArrayDeque<Object>();
        buildDependencies(rootClass, result, dependents);
        assert dependents.isEmpty() : dependents;
        return new ArrayList<Object>(result.values());
    }

    // public class methods

    public static Guicer forServices(Collection<ServiceBinding> serviceBindings)
    throws ClassNotFoundException
    {
        ArgumentValidation.notNull("bindings", serviceBindings);
        return new Guicer(serviceBindings);
    }

    // private methods

    private Guicer(Collection<ServiceBinding> serviceBindings)
    throws ClassNotFoundException
    {
        List<Class<?>> localDirectlyRequiredClasses = new ArrayList<Class<?>>();
        List<ResolvedServiceBinding> resolvedServiceBindings = new ArrayList<ResolvedServiceBinding>();

        for (ServiceBinding serviceBinding : serviceBindings) {
            ResolvedServiceBinding resolvedServiceBinding = new ResolvedServiceBinding(serviceBinding);
            resolvedServiceBindings.add(resolvedServiceBinding);
            if (serviceBinding.isDirectlyRequired()) {
                localDirectlyRequiredClasses.add(resolvedServiceBinding.serviceInterfaceClass());
            }
        }
        Collections.sort(localDirectlyRequiredClasses, BY_CLASS_NAME);
        directlyRequiredClasses = Collections.unmodifiableCollection(localDirectlyRequiredClasses);

        this.services = Collections.synchronizedSet(new LinkedHashSet<Object>());

        AbstractModule module = new ServiceBindingsModule(resolvedServiceBindings);
        _injector = Guice.createInjector(module);
    }

    private void buildDependencies(Class<?> forClass, LinkedHashMap<Class<?>,Object> results, Deque<Object> dependents) {
        Object instance = _injector.getInstance(forClass);
        if (dependents.contains(instance)) {
            throw new CircularDependencyException("circular dependency at " + forClass + ": " + dependents);
        }

        // Start building this object
        dependents.addLast(instance);

        Class<?> actualClass = instance.getClass();
        Object oldInstance = results.put(actualClass, instance);
        if (oldInstance != null) {
            assert oldInstance == instance : oldInstance + " != " + instance;
        }

        // Build the dependency list
        List<Class<?>> dependencyClasses = new ArrayList<Class<?>>();
        for (Dependency<?> dependency : InjectionPoint.forConstructorOf(actualClass).getDependencies()) {
            dependencyClasses.add(dependency.getKey().getTypeLiteral().getRawType());
        }
        for (InjectionPoint injectionPoint : InjectionPoint.forInstanceMethodsAndFields(actualClass)) {
            for (Dependency<?> dependency : injectionPoint.getDependencies()) {
                dependencyClasses.add(dependency.getKey().getTypeLiteral().getRawType());
            }
        }
        for (InjectionPoint injectionPoint : InjectionPoint.forStaticMethodsAndFields(actualClass)) {
            for (Dependency<?> dependency : injectionPoint.getDependencies()) {
                dependencyClasses.add(dependency.getKey().getTypeLiteral().getRawType());
            }
        }

        // Sort it and recursively invoke
        Collections.sort(dependencyClasses, BY_CLASS_NAME);
        for (Class<?> dependencyClass : dependencyClasses) {
            buildDependencies(dependencyClass, results, dependents);
        }

        // Done building the object; pop the deque and confirm the instance
        Object removed = dependents.removeLast();
        assert removed == instance : removed + " != " + instance;
    }

    private <T,S> T startService(Class<T> serviceClass, T instance, ServiceLifecycleActions<S> withActions) {
        // quick check; startServiceIfApplicable will do this too, but this way we can avoid finding dependencies
        if (services.contains(instance)) {
            return instance;
        }
        synchronized (services) {
            for (Object dependency : reverse(dependenciesFor(serviceClass))) {
                startServiceIfApplicable(dependency, withActions);
            }
        }
        return instance;
    }

    private static <T> List<T> reverse(List<T> list) {
        Collections.reverse(list);
        return list;
    }

    private <T, S> void startServiceIfApplicable(T instance, ServiceLifecycleActions<S> withActions) {
        if (services.contains(instance)) {
            return;
        }
        if (withActions == null) {
            services.add(instance);
            return;
        }
        S service = withActions.castIfActionable(instance);
        if (service != null) {
            try {
                withActions.onStart(service);
                services.add(service);
            } catch (Exception e) {
                try {
                    stopServices(withActions, e);
                } catch (Exception e1) {
                    e = e1;
                }
                throw new ProvisionException("While starting service " + instance.getClass(), e);
            }
        }
    }

    private void stopServices(ServiceLifecycleActions<?> withActions, Exception initialCause) throws Exception {
        List<Throwable> exceptions = tryStopServices(withActions, initialCause);
        if (!exceptions.isEmpty()) {
            if (exceptions.size() == 1) {
                throw Exceptions.throwAlways(exceptions, 0);
            }
            for (Throwable t : exceptions) {
                t.printStackTrace();
            }
            Throwable cause = exceptions.get(0);
            throw new Exception("Failure(s) while shutting down services: " + exceptions, cause);
        }
    }

    private <S> List<Throwable> tryStopServices(ServiceLifecycleActions<S> withActions, Exception initialCause) {
        ListIterator<?> reverseIter;
        synchronized (services) {
            reverseIter = new ArrayList<Object>(services).listIterator(services.size());
        }
        List<Throwable> exceptions = new ArrayList<Throwable>();
        if (initialCause != null) {
            exceptions.add(initialCause);
        }
        while (reverseIter.hasPrevious()) {
            try {
                Object serviceObject = reverseIter.previous();
                services.remove(serviceObject);
                if (withActions != null) {
                    S service = withActions.castIfActionable(serviceObject);
                    if (service != null) {
                        withActions.onShutdown(service);
                    }
                }
            } catch (Throwable t) {
                exceptions.add(t);
            }
        }
        // TODO because our dependency graph is created via Service.start() invocations, if service A uses service B
        // in stop() but not start(), and service B has already been shut down, service B will be resurrected. Yuck.
        // I don't know of a good way around this, other than by formalizing our dependency graph via constructor
        // params (and thus removing ServiceManagerImpl.get() ). Until this is resolved, simplest is to just shrug
        // our shoulders and not check
//        synchronized (lock) {
//            assert services.isEmpty() : services;
//        }
        return exceptions;
    }

    // object state

    private final Collection<Class<?>> directlyRequiredClasses;
    private final Set<Object> services;
    private final Injector _injector;

    // consts

    private static final Comparator<? super Class<?>> BY_CLASS_NAME = new Comparator<Class<?>>() {
        @Override
        public int compare(Class<?> o1, Class<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    // nested classes

    private static final class ResolvedServiceBinding {

        // ResolvedServiceBinding interface

        public Class<?> serviceInterfaceClass() {
            return serviceInterfaceClass;
        }

        public Class<?> serviceImplementationClass() {
            return serviceImplementationClass;
        }

        public ResolvedServiceBinding(ServiceBinding serviceBinding) throws ClassNotFoundException {
            this.serviceInterfaceClass = Class.forName(serviceBinding.getInterfaceName());
            this.serviceImplementationClass = Class.forName(serviceBinding.getImplementingClassName());
            if (!this.serviceInterfaceClass.isAssignableFrom(this.serviceImplementationClass)) {
                throw new IllegalArgumentException(this.serviceInterfaceClass + " is not assignable from "
                        + this.serviceImplementationClass);
            }
        }

        // object state
        private final Class<?> serviceInterfaceClass;
        private final Class<?> serviceImplementationClass;
    }

    private static final class ServiceBindingsModule extends AbstractModule {
        @Override
        // we use unchecked, raw Class, relying on the invariant established by ResolvedServiceBinding's ctor
        @SuppressWarnings("unchecked")
        protected void configure() {
            for (ResolvedServiceBinding binding : bindings) {
                Class unchecked = binding.serviceInterfaceClass();
                bind(unchecked).to(binding.serviceImplementationClass()).in(Scopes.SINGLETON);
            }
        }

        // ServiceBindingsModule interface

        private ServiceBindingsModule(Collection<ResolvedServiceBinding> bindings)
        {
            this.bindings = bindings;
        }

        // object state

        private final Collection<ResolvedServiceBinding> bindings;
    }

    static interface ServiceLifecycleActions<T> {
        void onStart(T service) throws Exception;
        void onShutdown(T service) throws Exception;

        /**
         * Cast the given object to the actionable type if possible, or return {@code null} otherwise.
         * @param object the object which may or may not be actionable
         * @return the object reference, correctly casted; or null
         */
        T castIfActionable(Object object);
    }

}
