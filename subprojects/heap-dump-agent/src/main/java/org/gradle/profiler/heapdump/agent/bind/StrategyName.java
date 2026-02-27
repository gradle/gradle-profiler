package org.gradle.profiler.heapdump.agent.bind;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy; /**
 * Marker annotation used to bind a fixed strategy name from the agent declaration to the advice method parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface StrategyName {
}
