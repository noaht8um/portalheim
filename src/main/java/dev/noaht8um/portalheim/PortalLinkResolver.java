package dev.noaht8um.portalheim;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PortalLinkResolver {
    private PortalLinkResolver() {
    }

    public static Set<BlockKey> resolveActiveSigns(Collection<PortalRecord> portals) {
        Set<BlockKey> activeSigns = new HashSet<>();
        Map<String, java.util.List<PortalRecord>> byTag = portals.stream()
            .filter(portal -> !portal.tag().isBlank())
            .collect(Collectors.groupingBy(PortalRecord::tag));

        byTag.values().forEach(group -> {
            if (group.size() == 2) {
                group.forEach(portal -> activeSigns.add(portal.sign()));
            }
        });

        return activeSigns;
    }
}
