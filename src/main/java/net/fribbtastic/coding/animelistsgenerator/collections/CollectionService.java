package net.fribbtastic.coding.animelistsgenerator.collections;

import net.fribbtastic.coding.animelistsgenerator.models.AnimeCollection;
import net.fribbtastic.coding.animelistsgenerator.models.AnimeItem;
import net.fribbtastic.coding.animelistsgenerator.utils.Properties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Frederic Eßer
 */
@Service
public class CollectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionService.class);

    // Limits and crossover tags are configurable (application.properties):
    //   collection.max-degree   (default 0 = no limit)
    //   collection.max-size     (default 300, 0 = no limit)
    //   collection.crossover-tags (default "crossover,crossover episode", empty = off)
    //   collection.crossover-exceptions (TV series still treated as crossovers; default:
    //                                   the bundled crossover-exceptions.json)

    private static final String CROSSOVER_EXCEPTIONS_FILE = "crossover-exceptions.json";
    private static final Set<String> KNOWN_SOURCES = Set.of("anidb", "anilist", "animecountdown", "animenewsnetwork",
            "anime-planet", "anisearch", "kitsu", "livechart", "mal", "simkl");

    private static final Comparator<String> MIXED_ID_COMPARATOR = (a, b) -> {
        boolean aNumeric = a.matches("\\d+");
        boolean bNumeric = b.matches("\\d+");

        if (aNumeric && bNumeric) {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        }

        // compare IMDB Ids
        if (a.startsWith("tt") && b.startsWith("tt")) {
            try {
                int aNum = Integer.parseInt(a.substring(2));
                int bNum = Integer.parseInt(b.substring(2));

                return Integer.compare(aNum, bNum);
            } catch (NumberFormatException e) {
                LOGGER.warn("could not parse IMDB ID '{}' and '{}'", a, b);
            }
        }

        return a.compareTo(b);
    };

    /**
     * generate the collections separated by source
     * The graph will only include mutual relations between the anime items
     *
     * @param animeItems the list of AnimeItems
     * @return the map of collections separated by source
     */
    public Map<String, List<AnimeCollection>> generateCollections(Collection<AnimeItem> animeItems) {

        Map<String, Graph<String, DefaultEdge>> graphs = new HashMap<>();
        Map<String, Map<String, AnimeItem>> lookup = new HashMap<>();

        // initialize the grap and lookup
        for (AnimeItem anime : animeItems) {
            Map<String, List<String>> relations = anime.getRelations();
            // don't need to do anything if there are no relations
            if(relations == null) {
                continue;
            }

            for (String source : relations.keySet()) {
                // skip anime-planet because the IDs are the titles that makes it unusable for the collections
                if(source.equals("anime-planet")) {
                    continue;
                }

                graphs.computeIfAbsent(source, s -> new SimpleGraph<>(DefaultEdge.class));
                for (String id : anime.getIdForSource(source)) {
                    lookup.computeIfAbsent(source, s -> new HashMap<>()).put(id, anime);
                }
            }
        }

        // build the graph
        for (AnimeItem anime : animeItems) {
            Map<String, List<String>> relations = anime.getRelations();
            // again, don't need to do anything if there are no relations
            if(relations == null) {
                continue;
            }

            for (Map.Entry<String, List<String>> entry : relations.entrySet()) {
                String source = entry.getKey();
                // again, skip anime-planet
                if(source.equals("anime-planet")) {
                    continue;
                }

                Graph<String, DefaultEdge> graph = graphs.get(source);
                List<String> selfIds = anime.getIdForSource(source);
                if (graph == null || selfIds == null) {
                    continue;
                }

                selfIds.forEach(graph::addVertex);

                for (String relationId : entry.getValue()) {
                    graph.addVertex(relationId);

                    for (String selfId : selfIds) {
                        if (isMutualRelation(lookup.get(source), selfId, relationId)) {
                            if (!selfId.equals(relationId))
                            {
                                graph.addEdge(selfId, relationId);
                            }
                        }
                    }
                }
            }
        }

        Set<String> crossoverTags = parseCrossoverTags(Properties.collectionCrossoverTags);
        Map<String, Set<String>> crossoverExceptions = crossoverTags.isEmpty()
                ? Map.of() : loadCrossoverExceptions(Properties.collectionCrossoverExceptions);
        int maxDegree = Properties.collectionMaxDegree;
        int maxSize = Properties.collectionMaxSize;
        LOGGER.info("generating collections: max-degree={}, max-size={}, crossover-tags={}",
                maxDegree > 0 ? maxDegree : "off", maxSize > 0 ? maxSize : "off",
                crossoverTags.isEmpty() ? "off" : crossoverTags);

        // extract collections
        Map<String, List<AnimeCollection>> result = new HashMap<>();
        for (Map.Entry<String, Graph<String, DefaultEdge>> entry : graphs.entrySet()) {
            String source = entry.getKey();
            Graph<String, DefaultEdge> graph = entry.getValue();
            Map<String, AnimeItem> sourceLookup = lookup.get(source);

            // Crossover entries (tagged e.g. "crossover") must not bridge two franchises:
            // remember the neighbours of all of them first (so links between crossover
            // entries are kept), then take them out of the graph and add them back afterwards.
            // TV series are not treated as crossovers (they are usually main series with
            // crossover episodes), except those listed in the crossover exceptions.
            Map<String, List<String>> crossoverNeighbours = new HashMap<>();
            if (!crossoverTags.isEmpty() && sourceLookup != null) {
                for (String vertex : graph.vertexSet()) {
                    if (isCrossover(sourceLookup.get(vertex), crossoverTags, crossoverExceptions)) {
                        crossoverNeighbours.put(vertex, Graphs.neighborListOf(graph, vertex));
                    }
                }
                crossoverNeighbours.keySet().forEach(graph::removeVertex);
            }

            // Remove large nodes to prevent giant clusters
            if (maxDegree > 0) {
                List<String> toRemove = graph.vertexSet().stream()
                        .filter(v -> graph.degreeOf(v) > maxDegree)
                        .toList();
                toRemove.forEach(graph::removeVertex);
            }

            ConnectivityInspector<String, DefaultEdge> inspector = new ConnectivityInspector<>(graph);
            List<Set<String>> connectedSets = new ArrayList<>();
            Map<String, Integer> setOfVertex = new HashMap<>();
            for (Set<String> connectedSet : inspector.connectedSets()) {
                Set<String> copy = new HashSet<>(connectedSet);
                for (String v : copy) {
                    setOfVertex.put(v, connectedSets.size());
                }
                connectedSets.add(copy);
            }

            // Crossover entries linked to each other form a crossover component (e.g. the
            // seasons of Isekai Quartet). Each component gets a group of its own, together with
            // the single entries related only to its crossover entries (e.g. their PVs or a
            // recap), named after its first crossover entry. A crossover entry on its own gets
            // one only if such single entries exist.
            Map<Set<String>, String> namedAfter = new IdentityHashMap<>();
            Map<String, Set<String>> relatedCrossovers = new HashMap<>();
            crossoverNeighbours.forEach((crossover, neighbours) ->
                    neighbours.forEach(n -> relatedCrossovers.computeIfAbsent(n, k -> new HashSet<>()).add(crossover)));
            Set<String> hasOwnGroup = new HashSet<>();
            Set<String> visited = new HashSet<>();
            for (String start : new TreeSet<>(crossoverNeighbours.keySet())) {
                if (!visited.add(start)) {
                    continue;
                }
                Set<String> component = new TreeSet<>(MIXED_ID_COMPARATOR);
                Deque<String> queue = new ArrayDeque<>(List.of(start));
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    component.add(current);
                    for (String neighbour : crossoverNeighbours.get(current)) {
                        if (crossoverNeighbours.containsKey(neighbour) && visited.add(neighbour)) {
                            queue.add(neighbour);
                        }
                    }
                }
                Set<String> members = new HashSet<>(component);
                for (String crossover : component) {
                    for (String neighbour : crossoverNeighbours.get(crossover)) {
                        Integer setIndex = setOfVertex.get(neighbour);
                        if (setIndex != null && connectedSets.get(setIndex).size() == 1
                                && component.containsAll(relatedCrossovers.get(neighbour))) {
                            members.addAll(connectedSets.get(setIndex));
                            connectedSets.get(setIndex).clear();
                            setOfVertex.remove(neighbour);
                        }
                    }
                }
                if (members.size() > 1) {
                    connectedSets.add(members);
                    namedAfter.put(members, component.iterator().next());
                    hasOwnGroup.addAll(component);
                }
            }

            // Crossover entries are also added to every group they are related to (those groups
            // are not merged). One related only to other crossover entries gets their groups (one
            // step, so chains of crossover entries don't spread everywhere). One without any
            // group stays on its own.
            Map<String, Set<Integer>> targets = new HashMap<>();
            for (Map.Entry<String, List<String>> crossover : crossoverNeighbours.entrySet()) {
                Set<Integer> groups = new TreeSet<>();
                for (String neighbour : crossover.getValue()) {
                    Integer setIndex = setOfVertex.get(neighbour);
                    if (setIndex != null) {
                        groups.add(setIndex);
                    }
                }
                targets.put(crossover.getKey(), groups);
            }
            Map<String, Set<Integer>> inherited = new HashMap<>();
            for (Map.Entry<String, Set<Integer>> crossover : targets.entrySet()) {
                if (crossover.getValue().isEmpty()) {
                    Set<Integer> groups = new TreeSet<>();
                    for (String neighbour : crossoverNeighbours.get(crossover.getKey())) {
                        groups.addAll(targets.getOrDefault(neighbour, Set.of()));
                    }
                    inherited.put(crossover.getKey(), groups);
                }
            }
            targets.putAll(inherited);
            for (Map.Entry<String, Set<Integer>> crossover : new TreeMap<>(targets).entrySet()) {
                for (Integer group : crossover.getValue()) {
                    connectedSets.get(group).add(crossover.getKey());
                }
                if (crossover.getValue().isEmpty() && !hasOwnGroup.contains(crossover.getKey())) {
                    connectedSets.add(new HashSet<>(Set.of(crossover.getKey())));
                }
            }
            connectedSets.removeIf(Set::isEmpty);

            List<AnimeCollection> collections = new ArrayList<>();
            for (Set<String> connectedSet : connectedSets) {
                // skip very large collections
                if (maxSize > 0 && connectedSet.size() > maxSize) {
                    continue;
                }

                List<String> sorted = new ArrayList<>(connectedSet);
                sorted.sort(MIXED_ID_COMPARATOR);

                // determine the name of the collection (from the group's own entries, not
                // from crossover entries that were added to it)
                // (a group of single entries related only to one crossover entry is named after it)
                List<String> naming = namedAfter.containsKey(connectedSet)
                        ? List.of(namedAfter.get(connectedSet))
                        : sorted.stream().filter(id -> !crossoverNeighbours.containsKey(id)).toList();
                String name = this.determineCollectionName(naming.isEmpty() ? sorted : naming, sourceLookup);

                // convert the ID to the correct format
                List<Object> convertedIds = sorted.stream().map(this::convertId).toList();

                collections.add(new AnimeCollection(name, convertedIds));
            }

            result.put(source, collections);
        }

        return result;
    }

    /**
     * parse the comma separated list of crossover tags (lower case, trimmed)
     */
    static Set<String> parseCrossoverTags(String value) {
        Set<String> tags = new HashSet<>();
        if (value == null) {
            return tags;
        }
        for (String tag : value.split(",")) {
            String trimmed = tag.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    /**
     * is the item a crossover entry? Tagged with one of the crossover tags, and not a TV
     * series unless it is listed in the crossover exceptions.
     */
    static boolean isCrossover(AnimeItem item, Set<String> crossoverTags, Map<String, Set<String>> exceptions) {
        if (item == null || item.getTags() == null) {
            return false;
        }
        boolean tagged = item.getTags().stream()
                .anyMatch(tag -> tag != null && crossoverTags.contains(tag.trim().toLowerCase(Locale.ROOT)));
        if (!tagged) {
            return false;
        }
        if (!"TV".equalsIgnoreCase(item.getType())) {
            return true;
        }
        for (Map.Entry<String, Set<String>> exception : exceptions.entrySet()) {
            for (String id : item.getIdForSource(exception.getKey())) {
                if (exception.getValue().contains(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Load the TV series that are still treated as crossover entries (e.g. "Isekai Quartet"):
     * a JSON array of objects with the entry's IDs per source, e.g.
     * {@code [{"title": "Isekai Quartet", "mal": 38472, "anidb": 14435}]}. "title" is only
     * for reading; an entry matches when any of its IDs is listed.
     *
     * @param location file path, or empty for the bundled crossover-exceptions.json
     * @return source -> listed IDs
     */
    static Map<String, Set<String>> loadCrossoverExceptions(String location) {
        Map<String, Set<String>> exceptions = new HashMap<>();
        try (InputStream in = location == null || location.isBlank()
                ? CollectionService.class.getResourceAsStream("/" + CROSSOVER_EXCEPTIONS_FILE)
                : Files.newInputStream(Path.of(location))) {
            if (in == null) {
                LOGGER.warn("crossover exceptions not found ({}), no TV series is treated as crossover", CROSSOVER_EXCEPTIONS_FILE);
                return exceptions;
            }
            JsonNode root = new ObjectMapper().readTree(in);
            for (JsonNode entry : root) {
                for (Map.Entry<String, JsonNode> field : entry.properties()) {
                    String source = field.getKey();
                    if (source.equals("title") || !KNOWN_SOURCES.contains(source)) {
                        continue;
                    }
                    List<JsonNode> ids = new ArrayList<>();
                    if (field.getValue().isArray()) {
                        field.getValue().forEach(ids::add);
                    } else {
                        ids.add(field.getValue());
                    }
                    for (JsonNode id : ids) {
                        exceptions.computeIfAbsent(source, k -> new HashSet<>()).add(id.asText());
                    }
                }
            }
            LOGGER.info("crossover exceptions: {} TV series", root.size());
        } catch (IOException e) {
            LOGGER.warn("could not read crossover exceptions ({}): {}", location, e.getMessage());
        }
        return exceptions;
    }

    private Object convertId(String id) {
        if (id != null && id.matches("\\d+")) {
            try {
                return Integer.parseInt(id);
            } catch (NumberFormatException e) {
                LOGGER.warn("could not parse ID '{}' as Integer", id);
            }
        }
        return id;
    }

    /**
     * Determine the name of the collection based on the sorted list of IDs.
     *
     * @param sortedList the sorted list of IDs
     * @param lookup the lookup map for the source
     * @return the name of the collection
     */
    private String determineCollectionName(List<String> sortedList, Map<String, AnimeItem> lookup) {

        if(sortedList == null || sortedList.isEmpty()) {
            return "Unknown";
        }

        // if there is no lookup map, just return the first ID as fallback
        if(lookup == null) {
            return sortedList.getFirst();
        }

        for (String id : sortedList) {
            AnimeItem anime = lookup.get(id);
            if(anime != null && anime.getTitle() != null) {
                return anime.getTitle(); // return the earliest title that is available
            }
        }

        return sortedList.getFirst(); // fallback to the first ID if no title is available
    }

    /**
     * Only connect when A and B are mutually related
     *
     * @param sourceLookup the lookup map for the source
     * @param a the ID of the first anime
     * @param b the ID of the second anime
     * @return true if A and B are mutually related, false otherwise
     */
    private boolean isMutualRelation(Map<String, AnimeItem> sourceLookup, String a, String b) {
        if (sourceLookup == null) {
            return true;
        }

        AnimeItem other = sourceLookup.get(b);
        if (other == null || other.getRelations() == null) {
            return false;
        }

        for (List<String> relations : other.getRelations().values()) {
            if (relations.contains(a)) {
                return true;
            }
        }

        return false;
    }
}
