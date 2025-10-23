package cbr;

import org.apache.jena.rdf.model.Model;
import ucm.gaia.jcolibri.exception.ExecutionException;
import ucm.gaia.jcolibri.method.retrieve.RetrievalResult;

import java.util.*;
import java.util.stream.Collectors;

public class CaseBasedReasoning {

    private FilmCbrApplication cbrApp;

    public CaseBasedReasoning(Model model) {
        this.cbrApp = new FilmCbrApplication(model);
        try {
            cbrApp.configure();
            cbrApp.preCycle();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public List<SimilarFilm> findSimilarFilms(String targetFilmTitle, int topN) {
        try {
            Collection<RetrievalResult> results = cbrApp.findSimilarFilms(targetFilmTitle, topN);

            if (results == null) {
                return Collections.emptyList();
            }

            List<SimilarFilm> similarFilms = new ArrayList<>();

            for (RetrievalResult result : results) {
                CaseDescription desc = (CaseDescription) result.get_case().getDescription();

                // converts jCOLIBRI similarity (0-1) to score 0-100
                double score = result.getEval() * 100;

                SimilarFilm film = new SimilarFilm(
                        desc.getTitle(),
                        score,
                        desc.getYear(),
                        desc.getImdbRating(),
                        desc.getDirector(),
                        parseGenres(desc.getGenres())
                );

                similarFilms.add(film);
            }

            return similarFilms;

        } catch (ExecutionException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private Set<String> parseGenres(String genresStr) {
        if (genresStr == null || genresStr.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(genresStr.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    public static class SimilarFilm {
        private String title;
        private double score;
        private int year;
        private double imdbRating;
        private String director;
        private Set<String> genres;

        public SimilarFilm(String title, double score, int year, double imdbRating,
                           String director, Set<String> genres) {
            this.title = title;
            this.score = score;
            this.year = year;
            this.imdbRating = imdbRating;
            this.director = director;
            this.genres = genres;
        }

        public String getTitle() { return title; }
        public double getScore() { return score; }
        public int getYear() { return year; }
        public double getImdbRating() { return imdbRating; }
        public String getDirector() { return director; }
        public Set<String> getGenres() { return genres; }
    }
}