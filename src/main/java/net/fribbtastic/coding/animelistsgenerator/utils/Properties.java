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
    /** Relations (comma separated, see RelationParser.normalise) that don't join two anime into one collection */
    public static String collectionExcludedRelations = "crossover,other,shared universe,character";

    @Value("${collection.max-degree:0}")
    public void setCollectionMaxDegree(int value) {
        collectionMaxDegree = value;
    }

    @Value("${collection.max-size:300}")
    public void setCollectionMaxSize(int value) {
        collectionMaxSize = value;
    }

    @Value("${collection.excluded-relations:crossover,other,shared universe,character}")
    public void setCollectionExcludedRelations(String value) {
        collectionExcludedRelations = value;
    }

    /** Local anime-offline-database-minified.json to read instead of downloading it; empty = download */
    public static String animeOfflineDatabaseFile = "";

    @Value("${anime-offline-database.file:}")
    public void setAnimeOfflineDatabaseFile(String value) {
        animeOfflineDatabaseFile = value;
    }
}
