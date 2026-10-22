package net.fribbtastic.coding.animelistsgenerator.collections;

import net.fribbtastic.coding.animelistsgenerator.models.AnimeCollection;
import net.fribbtastic.coding.animelistsgenerator.models.AnimeItem;
import net.fribbtastic.coding.animelistsgenerator.utils.Properties;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
            // remember their neighbours, take them out of the graph, and add them to every
            // group they are related to afterwards.
            Map<String, List<String>> crossoverNeighbours = new HashMap<>();
            if (!crossoverTags.isEmpty() && sourceLookup != null) {
                for (String vertex : new ArrayList<>(graph.vertexSet())) {
                    if (isCrossover(sourceLookup.get(vertex), crossoverTags)) {
                        crossoverNeighbours.put(vertex, Graphs.neighborListOf(graph, vertex));
                        graph.removeVertex(vertex);
                    }
                }
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

            // Crossover entries are added to every group they are related to (they can
            // belong to several franchises); with no related group they stay on their own.
            for (Map.Entry<String, List<String>> crossover : crossoverNeighbours.entrySet()) {
                Set<Integer> targets = new TreeSet<>();
                for (String neighbour : crossover.getValue()) {
                    Integer setIndex = setOfVertex.get(neighbour);
                    if (setIndex != null) {
                        targets.add(setIndex);
                    }
                }
                if (targets.isEmpty()) {
                    connectedSets.add(new HashSet<>(Set.of(crossover.getKey())));
                } else {
                    for (Integer target : targets) {
                        connectedSets.get(target).add(crossover.getKey());
                    }
                }
            }

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
                List<String> naming = sorted.stream().filter(id -> !crossoverNeighbours.containsKey(id)).toList();
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
     * is the item tagged as a crossover entry?
     */
    static boolean isCrossover(AnimeItem item, Set<String> crossoverTags) {
        if (item == null || item.getTags() == null) {
            return false;
        }
        for (String tag : item.getTags()) {
            if (tag != null && crossoverTags.contains(tag.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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
