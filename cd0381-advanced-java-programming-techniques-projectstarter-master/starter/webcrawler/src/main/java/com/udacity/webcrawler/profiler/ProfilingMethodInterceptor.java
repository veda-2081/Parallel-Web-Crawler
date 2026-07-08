package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final ProfilingState state;
  private final Object delegate;

  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, ProfilingState state, Object delegate) {
    this.clock = Objects.requireNonNull(clock);
    this.state = Objects.requireNonNull(state);
    this.delegate = Objects.requireNonNull(delegate);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.
    if (method.getDeclaringClass()==Object.class && method.getName().equals("equals")) {
      return method.invoke(delegate, args);
    }
      /*switch (method.getName()){
        case "toString":
          return delegate.toString();
        case "hashCode":
          return delegate.hashCode();
        case "equals":
          return proxy == args[0];
        default:
          return method.invoke(delegate, args);
      }
    }*/
    boolean isProfiled = method.isAnnotationPresent(Profiled.class);
    long start = isProfiled ? clock.millis() : 0;
    try {
      Object result = method.invoke(delegate, args);
      if (isProfiled) {
        state.record(delegate.getClass(),method,java.time.Duration.ofMillis(clock.millis()-start));
      }
      return result;
    } catch (Throwable t) {
      if (isProfiled) {
        state.record(delegate.getClass(),method,java.time.Duration.ofMillis(clock.millis()-start));
      }

      if(t instanceof java.lang.reflect.InvocationTargetException){
        Throwable cause = t.getCause();
        throw (cause != null) ? cause : t;
      }
      if(t instanceof IllegalAccessException){
        throw new RuntimeException(t);
      }
      /**/
      throw t;
    }
    //return null;
  }
}
