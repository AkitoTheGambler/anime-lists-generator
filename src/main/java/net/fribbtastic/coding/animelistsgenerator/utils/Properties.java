package net.fribbtastic.coding.animelistsgenerator.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Frederic Eßer
 */
@Component
public class Properties {

    public static String projectPath;
    public static String theMovieDbApiKey;

    @Value("${project.path}")
    public void setProjectPath(String path) {
        projectPath = path;
    }

    @Value("${project.apikey.themoviedb}")
    public void setTheMovieDbApiKey(String apiKey) {
        theMovieDbApiKey = apiKey;
    }

    /** Remove entries with more mutual relations than this before grouping (0 = no limit) */
    public static int collectionMaxDegree = 0;
    /** Drop collections larger than this (0 = no limit) */
    public static int collectionMaxSize = 300;
    /** Tags (case-insensitive, comma separated) marking crossover entries; empty = feature off */
    public static String collectionCrossoverTags = "crossover,crossover episode";

    @Value("${collection.max-degree:0}")
    public void setCollectionMaxDegree(int value) {
        collectionMaxDegree = value;
    }

    @Value("${collection.max-size:300}")
    public void setCollectionMaxSize(int value) {
        collectionMaxSize = value;
    }

    @Value("${collection.crossover-tags:crossover,crossover episode}")
    public void setCollectionCrossoverTags(String value) {
        collectionCrossoverTags = value;
    }

    /** JSON file with TV series that are still treated as crossovers; empty = bundled crossover-exceptions.json */
    public static String collectionCrossoverExceptions = "";

    @Value("${collection.crossover-exceptions:}")
    public void setCollectionCrossoverExceptions(String value) {
        collectionCrossoverExceptions = value;
    }
}
