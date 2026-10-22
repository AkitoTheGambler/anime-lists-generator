package net.fribbtastic.coding.animelistsgenerator.collections;

import net.fribbtastic.coding.animelistsgenerator.models.AnimeCollection;
import net.fribbtastic.coding.animelistsgenerator.models.AnimeItem;
import net.fribbtastic.coding.animelistsgenerator.utils.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Collections with crossover entries (collection.crossover-tags and the bundled
 * crossover-exceptions.json)
 */
class CollectionServiceTest {

    private int maxDegree;
    private int maxSize;
    private String crossoverTags;
    private String crossoverExceptions;

    @BeforeEach
    void setUp() {
        this.maxDegree = Properties.collectionMaxDegree;
        this.maxSize = Properties.collectionMaxSize;
        this.crossoverTags = Properties.collectionCrossoverTags;
        this.crossoverExceptions = Properties.collectionCrossoverExceptions;

        Properties.collectionMaxDegree = 0;
        Properties.collectionMaxSize = 300;
        Properties.collectionCrossoverTags = "crossover,crossover episode";
        Properties.collectionCrossoverExceptions = "";
    }

    @AfterEach
    void tearDown() {
        Properties.collectionMaxDegree = this.maxDegree;
        Properties.collectionMaxSize = this.maxSize;
        Properties.collectionCrossoverTags = this.crossoverTags;
        Properties.collectionCrossoverExceptions = this.crossoverExceptions;
    }

    @Test
    @DisplayName("Test: a crossover special is in both franchises, which are not merged")
    void testCrossoverJoinsBothFranchises() {
        List<AnimeCollection> collections = generate(
                anime(10, "Franchise A", "TV", 11, 1), anime(11, "Franchise A Season 2", "TV", 10),
                anime(20, "Franchise B", "TV", 21, 1), anime(21, "Franchise B Season 2", "TV", 20),
                crossover(1, "A x B Special", "SPECIAL", 10, 20));

        assertEquals(List.of("Franchise A", "Franchise B"), names(containing(collections, 1)));
        assertFalse(containing(collections, 10).getFirst().getIdList().contains(20));
        assertEquals(List.of(1, 10, 11), ids(collection(collections, "Franchise A")));
    }

    @Test
    @DisplayName("Test: a crossover related only to another crossover gets its collections")
    void testCrossoverRelatedOnlyToCrossover() {
        List<AnimeCollection> collections = generate(
                anime(10, "Franchise A", "TV", 11, 1), anime(11, "Franchise A Season 2", "TV", 10),
                anime(20, "Franchise B", "TV", 21, 1), anime(21, "Franchise B Season 2", "TV", 20),
                crossover(1, "A x B Special", "SPECIAL", 10, 20, 2),
                crossover(2, "A x B Special Recap", "SPECIAL", 1));

        assertEquals(List.of("Franchise A", "Franchise B"), names(containing(collections, 2)));
    }

    @Test
    @DisplayName("Test: crossovers related only to each other stay together")
    void testCrossoversRelatedOnlyToEachOther() {
        List<AnimeCollection> collections = generate(
                crossover(5, "Crossover Z", "OVA", 6),
                crossover(6, "Crossover W", "OVA", 5));

        assertEquals(1, containing(collections, 5).size());
        assertEquals(List.of(5, 6), ids(containing(collections, 5).getFirst()));
    }

    @Test
    @DisplayName("Test: a TV series tagged crossover is a normal entry (main series with crossover episodes)")
    void testTvSeriesIsNotCrossover() {
        List<AnimeCollection> collections = generate(
                crossover(235, "Meitantei Conan", "TV", 300, 301, 400),
                anime(300, "Meitantei Conan Movie 01", "MOVIE", 235),
                anime(301, "Meitantei Conan Movie 02", "MOVIE", 235),
                crossover(400, "Conan vs. Kid Special", "SPECIAL", 235, 401),
                anime(401, "Magic Kaito", "TV", 400, 402), anime(402, "Magic Kaito 1412", "TV", 401));

        assertEquals(1, containing(collections, 235).size());
        assertEquals(List.of(235, 300, 301, 400), ids(collection(collections, "Meitantei Conan")));
        assertEquals(List.of("Magic Kaito", "Meitantei Conan"), names(containing(collections, 400)));
    }

    @Test
    @DisplayName("Test: a TV series listed in crossover-exceptions.json is a crossover")
    void testTvSeriesListedAsException() {
        // Isekai Quartet (MAL 38472) is listed in the bundled crossover-exceptions.json
        List<AnimeCollection> collections = generate(
                crossover(38472, "Isekai Quartet", "TV", 910, 920),
                anime(910, "Overlord", "TV", 911, 38472), anime(911, "Overlord II", "TV", 910),
                anime(920, "Re:Zero", "TV", 921, 38472), anime(921, "Re:Zero 2nd Season", "TV", 920));

        assertEquals(List.of("Overlord", "Re:Zero"), names(containing(collections, 38472)));
        assertFalse(containing(collections, 910).getFirst().getIdList().contains(920));
    }

    @Test
    @DisplayName("Test: single entries related only to one crossover form one collection named after it")
    void testSingleEntriesOfCrossover() {
        List<AnimeCollection> collections = generate(
                crossover(700, "Arknights", "SPECIAL", 701, 702, 703, 710),
                anime(701, "Music Video 1", "MUSIC", 700), anime(702, "Music Video 2", "MUSIC", 700),
                anime(703, "Music Video 3", "MUSIC", 700),
                anime(710, "BanG Dream!", "TV", 711, 700), anime(711, "BanG Dream! 2nd Season", "TV", 710));

        assertEquals(List.of(700, 701, 702, 703), ids(collection(collections, "Arknights")));
        assertEquals(List.of("Arknights", "BanG Dream!"), names(containing(collections, 700)));
    }

    @Test
    @DisplayName("Test: without crossover tags a crossover merges the franchises (previous behaviour)")
    void testCrossoverTagsOff() {
        Properties.collectionCrossoverTags = "";
        List<AnimeCollection> collections = generate(
                anime(10, "Franchise A", "TV", 11, 1), anime(11, "Franchise A Season 2", "TV", 10),
                anime(20, "Franchise B", "TV", 21, 1), anime(21, "Franchise B Season 2", "TV", 20),
                crossover(1, "A x B Special", "SPECIAL", 10, 20));

        assertEquals(List.of(1, 10, 11, 20, 21), ids(containing(collections, 10).getFirst()));
    }

    @Test
    @DisplayName("Test: collection.max-size and collection.max-degree")
    void testLimits() {
        AnimeItem[] items = {
                anime(10, "Hub", "TV", 11, 12, 13), anime(11, "Hub Movie 1", "MOVIE", 10),
                anime(12, "Hub Movie 2", "MOVIE", 10), anime(13, "Hub Movie 3", "MOVIE", 10)};

        Properties.collectionMaxSize = 3;
        assertTrue(containing(generate(items), 10).isEmpty());

        Properties.collectionMaxSize = 300;
        Properties.collectionMaxDegree = 2;
        assertTrue(containing(generate(items), 10).isEmpty());
        assertEquals(List.of(11), ids(containing(generate(items), 11).getFirst()));
    }

    // --- helpers ---

    private static List<AnimeCollection> generate(AnimeItem... items) {
        return new CollectionService().generateCollections(List.of(items)).getOrDefault("mal", List.of());
    }

    private static AnimeItem anime(int malId, String title, String type, int... related) {
        AnimeItem item = new AnimeItem();
        item.setMyanimelist(malId);
        item.setTitle(title);
        item.setType(type);
        item.setRelations(Map.of("mal", Arrays.stream(related).mapToObj(String::valueOf).toList()));
        return item;
    }

    private static AnimeItem crossover(int malId, String title, String type, int... related) {
        AnimeItem item = anime(malId, title, type, related);
        item.setTags(List.of("crossover"));
        return item;
    }

    private static List<AnimeCollection> containing(List<AnimeCollection> collections, int id) {
        return collections.stream().filter(c -> c.getIdList().contains(id)).toList();
    }

    private static AnimeCollection collection(List<AnimeCollection> collections, String name) {
        return collections.stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no collection named " + name + ": " + names(collections)));
    }

    private static List<String> names(List<AnimeCollection> collections) {
        return collections.stream().map(AnimeCollection::getName).sorted().toList();
    }

    private static List<Integer> ids(AnimeCollection collection) {
        return collection.getIdList().stream().map(id -> (Integer) id).sorted().toList();
    }
}
