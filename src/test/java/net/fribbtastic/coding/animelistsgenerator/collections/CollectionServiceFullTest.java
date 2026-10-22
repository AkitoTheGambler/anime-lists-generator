package net.fribbtastic.coding.animelistsgenerator.collections;

import net.fribbtastic.coding.animelistsgenerator.Generator;
import net.fribbtastic.coding.animelistsgenerator.animeLists.service.AnimeListsService;
import net.fribbtastic.coding.animelistsgenerator.animeOfflineDatabase.service.AnimeOfflineDatabaseService;
import net.fribbtastic.coding.animelistsgenerator.models.AnimeCollection;
import net.fribbtastic.coding.animelistsgenerator.models.AnimeItem;
import net.fribbtastic.coding.animelistsgenerator.utils.Properties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Crossover handling of the collections against the full test data
 * (src/test/resources/files, see the "test" release)
 */
@SpringBootTest
@ActiveProfiles("test-full")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionServiceFullTest {

    @Autowired
    private AnimeOfflineDatabaseService animeOfflineDatabaseService;
    @Autowired
    private AnimeListsService animeListsService;
    @Autowired
    private CollectionService collectionService;
    @Autowired
    private Generator generator;

    private Map<String, List<AnimeCollection>> collections;

    private int maxDegree;
    private int maxSize;
    private String crossoverTags;
    private String crossoverExceptions;

    @BeforeAll
    void generateCollections() {
        this.maxDegree = Properties.collectionMaxDegree;
        this.maxSize = Properties.collectionMaxSize;
        this.crossoverTags = Properties.collectionCrossoverTags;
        this.crossoverExceptions = Properties.collectionCrossoverExceptions;

        // the defaults, with the bundled crossover-exceptions.json
        Properties.collectionMaxDegree = 0;
        Properties.collectionMaxSize = 300;
        Properties.collectionCrossoverTags = "crossover,crossover episode";
        Properties.collectionCrossoverExceptions = "";

        ArrayList<AnimeItem> aodbList = this.animeOfflineDatabaseService.generateList();
        ArrayList<AnimeItem> animeListsList = this.animeListsService.generateList();
        ArrayList<AnimeItem> mergedList = this.generator.mergeLists(animeListsList, aodbList);
        this.collections = this.collectionService.generateCollections(mergedList);
    }

    @AfterAll
    void restoreProperties() {
        Properties.collectionMaxDegree = this.maxDegree;
        Properties.collectionMaxSize = this.maxSize;
        Properties.collectionCrossoverTags = this.crossoverTags;
        Properties.collectionCrossoverExceptions = this.crossoverExceptions;
    }

    @Test
    @DisplayName("Test: Detective Conan (TV series tagged crossover) stays one collection")
    void testMeitanteiConan() {
        // MAL 235 / AniDB 266: TV series tagged crossover because of crossover episodes
        Assertions.assertThat(names("mal", 235)).containsExactly("Meitantei Conan");
        Assertions.assertThat(collection("mal", "Meitantei Conan").getIdList())
                .contains(779, 1367); // Movie 01, Movie 08 (Movie 08 is tagged crossover itself)

        Assertions.assertThat(names("anidb", 266)).containsExactly("Meitantei Conan");
    }

    @Test
    @DisplayName("Test: Isekai Quartet (listed exception) is in its franchises and has its own collection")
    void testIsekaiQuartet() {
        // MAL 38472, 39988, 61851: TV seasons listed in crossover-exceptions.json, 41567: the movie
        Assertions.assertThat(collection("mal", "Isekai Quartet").getIdList())
                .contains(38472, 39988, 41567, 61851);
        Assertions.assertThat(names("mal", 38472)).contains(
                "Isekai Quartet", "Overlord", "Kono Subarashii Sekai ni Shukufuku wo!",
                "Re:Zero kara Hajimeru Isekai Seikatsu", "Youjo Senki");

        // the franchises are not merged: Re:Zero (MAL 31240) is not in the Overlord collection
        Assertions.assertThat(collection("mal", "Overlord").getIdList()).doesNotContain(31240);

        Assertions.assertThat(collection("anidb", "Isekai Quartet").getIdList()).contains(14435, 14964, 19385);
        Assertions.assertThat(names("anidb", 14435)).contains("Isekai Quartet", "Overlord", "Youjo Senki");
    }

    @Test
    @DisplayName("Test: Toriko (listed exception) is not merged into One Piece")
    void testToriko() {
        // AniDB: all Toriko entries in one collection
        Assertions.assertThat(ids("anidb", 8142)).hasSize(1);
        Assertions.assertThat(ids("anidb", 8142).getFirst()).containsExactlyInAnyOrder(7227, 7735, 8009, 8142, 9671);

        // MAL: Toriko (10033) is related to One Piece (21) because of crossover episodes;
        // its collections contain its movies and specials, but not One Piece
        Assertions.assertThat(ids("mal", 10033)).isNotEmpty()
                .allSatisfy(collectionIds -> Assertions.assertThat(collectionIds).doesNotContain(21));
        Assertions.assertThat(ids("mal", 10074)).allSatisfy(collectionIds -> Assertions.assertThat(collectionIds).contains(10033));
        Assertions.assertThat(ids("mal", 11787)).allSatisfy(collectionIds -> Assertions.assertThat(collectionIds).contains(10033));
    }

    // --- helpers ---

    private List<AnimeCollection> containing(String source, int id) {
        return this.collections.get(source).stream().filter(c -> c.getIdList().contains(id)).toList();
    }

    private List<String> names(String source, int id) {
        return containing(source, id).stream().map(AnimeCollection::getName).toList();
    }

    private List<List<Object>> ids(String source, int id) {
        return containing(source, id).stream().map(AnimeCollection::getIdList).toList();
    }

    private AnimeCollection collection(String source, String name) {
        return this.collections.get(source).stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + source + " collection named " + name));
    }
}
