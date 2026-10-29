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

    /** Relations (comma separated, see RelationParser.normalise) that don't join two anime into one collection */
    public static String collectionExcludedRelations = "crossover,other,shared universe,character";

    @Value("${collection.excluded-relations:crossover,other,shared universe,character}")
    public void setCollectionExcludedRelations(String value) {
        collectionExcludedRelations = value;
    }

    /** Put groups cut off only by excluded relations back into their collection, if it is the only one they touch */
    public static boolean collectionRejoinSingleNeighbour = true;

    @Value("${collection.rejoin-single-neighbour:true}")
    public void setCollectionRejoinSingleNeighbour(boolean value) {
        collectionRejoinSingleNeighbour = value;
    }

    /** Local anime-offline-database-minified.json to read instead of downloading it; empty = download */
    public static String animeOfflineDatabaseFile = "";

    @Value("${anime-offline-database.file:}")
    public void setAnimeOfflineDatabaseFile(String value) {
        animeOfflineDatabaseFile = value;
    }
}
