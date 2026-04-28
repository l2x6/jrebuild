package org.l2x6.jrebuild.core.build;

import java.util.List;
import java.util.Map;

public record CompoundResourceMatch(
        ResourceMatchLevel resourceMatch,
        Map<ResourceMatchLevel, List<String>> componentMatches) {
}
