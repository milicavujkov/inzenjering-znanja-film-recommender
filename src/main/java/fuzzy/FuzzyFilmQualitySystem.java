package fuzzy;

import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;

import java.util.HashSet;
import java.util.Set;

public class FuzzyFilmQualitySystem {

    private FIS fis;
    private FunctionBlock fb;

    public FuzzyFilmQualitySystem() {
        String fclPath = "src/main/java/fuzzy/film_quality.fcl";
        fis = FIS.load(fclPath, true);

        if (fis == null) {
            throw new RuntimeException("Cannot load FCL file: film_quality.fcl");
        }

        fb = fis.getFunctionBlock("filmQuality");
    }

    public FilmQualityResult evaluateFilm(String filmTitle, Model model) {
        FilmData data = extractFilmData(filmTitle, model);

        if (data == null) {
            return null;
        }

        // calculate criteria
        double directorQuality = calculateDirectorQuality(data);
        double actingQuality = calculateActingQuality(data);
        double storyQuality = calculateStoryQuality(data);
        double visualEffects = calculateVisualEffects(data);
        double culturalImpact = calculateCulturalImpact(data);

        // set fuzzy inputs
        fb.setVariable("directorQuality", directorQuality);
        fb.setVariable("actingQuality", actingQuality);
        fb.setVariable("storyQuality", storyQuality);
        fb.setVariable("visualEffects", visualEffects);
        fb.setVariable("culturalImpact", culturalImpact);

        fb.evaluate();

        double quality = fb.getVariable("quality").getValue();

        return new FilmQualityResult(filmTitle, quality, data,
                directorQuality, actingQuality, storyQuality, visualEffects, culturalImpact);
    }

    private FilmData extractFilmData(String filmTitle, Model model) {
        // get basic film data and genres
        String sparql1 =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?imdb ?boxOffice ?budget ?year " +
                        "       (GROUP_CONCAT(DISTINCT ?genreName; separator=\",\") AS ?genres) " +
                        "WHERE { " +
                        "  ?film rdf:type :Film ; " +
                        "        :title ?title ; " +
                        "        :imdbRating ?imdb . " +
                        "  OPTIONAL { ?film :boxOfficeUSD ?boxOffice } " +
                        "  OPTIONAL { ?film :budgetUSD ?budget } " +
                        "  OPTIONAL { ?film :releaseYear ?year } " +
                        "  OPTIONAL { ?film :hasGenre ?g . ?g :genreName ?genreName } " +
                        "  FILTER(LCASE(STR(?title)) = LCASE(\"" + filmTitle + "\")) " +
                        "} " +
                        "GROUP BY ?film ?imdb ?boxOffice ?budget ?year";

        // defaults
        double imdb = 0, boxOffice = 0, budget = 1;
        int year = 2000;
        String genres = "";

        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql1))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            if (!rs.hasNext()) {
                return null;
            }

            QuerySolution sol = rs.next();
            imdb = sol.getLiteral("imdb").getDouble();
            boxOffice = sol.contains("boxOffice") ? sol.getLiteral("boxOffice").getDouble() : 0;
            budget = sol.contains("budget") ? sol.getLiteral("budget").getDouble() : 1;
            genres = sol.contains("genres") ? sol.getLiteral("genres").getString() : "";

            if (sol.contains("year")) {
                String yearStr = sol.getLiteral("year").getString();
                year = Integer.parseInt(yearStr.substring(0, 4));
            }
        }

        // get awards (categorized)
        String sparql2 =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?awardName " +
                        "WHERE { " +
                        "  ?film rdf:type :Film ; " +
                        "        :title ?title ; " +
                        "        :wonAward ?award . " +
                        "  ?award :awardName ?awardName . " +
                        "  FILTER(LCASE(STR(?title)) = LCASE(\"" + filmTitle + "\")) " +
                        "}";

        Set<String> awards = new HashSet<>();
        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql2))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                awards.add(rs.next().getLiteral("awardName").getString());
            }
        }

        return new FilmData(imdb, boxOffice, budget, year, genres, awards);
    }

    private double calculateDirectorQuality(FilmData data) {
        // check for director awards
        boolean hasDirectorAward = data.awards.stream()
                .anyMatch(a -> a.contains("Director"));

        if (hasDirectorAward) {
            // 95% from award and 5% from IMDb
            return 9.5 + (data.imdbRating * 0.05);
        } else {
            return data.imdbRating;
        }
    }

    private double calculateActingQuality(FilmData data) {
        // check for acting awards
        boolean hasActingAward = data.awards.stream()
                .anyMatch(a -> a.contains("Actor") || a.contains("Actress"));

        if (hasActingAward) {
            return 9.5 + (data.imdbRating * 0.05);
        } else {
            return data.imdbRating;
        }
    }

    private double calculateStoryQuality(FilmData data) {
        // check for screenplay awards
        boolean hasScreenplayAward = data.awards.stream()
                .anyMatch(a -> a.contains("Screenplay") || a.contains("Picture"));

        if (hasScreenplayAward) {
            return 9.5 + (data.imdbRating * 0.05);
        } else {
            return data.imdbRating * 0.95;
        }
    }

    private double calculateVisualEffects(FilmData data) {
        // check for VFX/Cinematography awards
        boolean hasVFXAward = data.awards.stream()
                .anyMatch(a -> a.contains("Visual Effects") ||
                        a.contains("Cinematography") ||
                        a.contains("Editing"));

        if (hasVFXAward) {
            // 95% from award and 5% from budget
            return 9.5 + (calculateVFXFromBudget(data) * 0.05);
        } else {
            return calculateVFXFromBudget(data);
        }
    }

    private double calculateVFXFromBudget(FilmData data) {
        // VFX heavy genres
        boolean isVFXGenre = data.genres != null &&
                (data.genres.contains("SciFi") ||
                        data.genres.contains("Fantasy") ||
                        data.genres.contains("Action") ||
                        data.genres.contains("Animation")
                );

        if (isVFXGenre) {
            if (data.genres.contains("Animation")) {
                if (data.imdbRating > 8.0) return 9.0;
                if (data.imdbRating > 7.0) return 8.0;
                return 7.0;
            }
            if (data.budget > 150_000_000) {
                if (factorInROI(data)) {
                    return 2.0;
                }
                return 9.0;
            }
            if (data.budget > 100_000_000) {
                if (factorInROI(data)) {
                    return 2.0;
                }
                return 8.0;
            }
            if (data.budget > 50_000_000) {
                if (factorInROI(data)) {
                    return 2.0;
                }
                return 7.0;
            }
            if (data.budget < 50_000_000) {
                if (factorInROI(data)) {
                    return 2.0;
                }
                return 6.0;
            }
        }

        // for other genres
        return 5.0;
    }

    private boolean factorInROI(FilmData data) {
        // ako je ROI nizak i nema vfx awards onda se ocena smanjuje
        double roi = data.budget > 0 ? data.boxOffice / data.budget : 0;

        if (roi < 1.0 && data.imdbRating < 3.5) {  // bomb i losa ocena
            return true;
        }
        return false;
    }

    private double calculateCulturalImpact(FilmData data) {
        int age = 2025 - data.releaseYear;
        double impact = data.imdbRating;

        // recent masterpiece
        if (age < 10 && data.imdbRating > 8.5) {
            impact += 1.0;
        }

        // classic masterpiece
        if (age > 30 && data.imdbRating > 8.0) {
            impact += 1.5;
        }

        // awards boost (any major award)
        if (!data.awards.isEmpty()) {
            impact += Math.min(data.awards.size() * 0.2, 1.5);
        }

        // box office success adds to cultural impact
        if (data.budget > 0) {
            double ratio = data.boxOffice / data.budget;
            if (ratio > 5.0) {  // 5x return is a major cultural phenomenon
                impact += 0.5;
            }
        }

        return Math.min(impact, 10.0);
    }


    private static class FilmData {
        double imdbRating;
        double boxOffice;
        double budget;
        int releaseYear;
        String genres;
        Set<String> awards;

        FilmData(double imdb, double boxOffice, double budget, int year, String genres, Set<String> awards) {
            this.imdbRating = imdb;
            this.boxOffice = boxOffice;
            this.budget = budget;
            this.releaseYear = year;
            this.genres = genres;
            this.awards = awards;
        }
    }

    public static class FilmQualityResult {
        public String filmTitle;
        public double qualityScore;
        public String qualityRating;

        // fuzzy inputs
        public double directorQuality;
        public double actingQuality;
        public double storyQuality;
        public double visualEffects;
        public double culturalImpact;

        // original data
        public double imdbRating;
        public double boxOffice;
        public double budget;
        public int releaseYear;
        public Set<String> awards;

        FilmQualityResult(String title, double quality, FilmData data,
                          double director, double acting, double story, double vfx, double culture) {
            this.filmTitle = title;
            this.qualityScore = quality;
            this.qualityRating = getRating(quality);

            this.directorQuality = director;
            this.actingQuality = acting;
            this.storyQuality = story;
            this.visualEffects = vfx;
            this.culturalImpact = culture;

            this.imdbRating = data.imdbRating;
            this.boxOffice = data.boxOffice;
            this.budget = data.budget;
            this.releaseYear = data.releaseYear;
            this.awards = data.awards;
        }

        private String getRating(double quality) {
            if (quality < 40) return "POOR";
            if (quality < 70) return "GOOD";
            return "EXCELLENT";
        }

        @Override
        public String toString() {
            String awardsStr = awards.isEmpty() ? "None" : String.join(", ", awards);
            double roi = budget > 0 ? (boxOffice / budget) : 0;

            return String.format(
                            "\nFILM QUALITY ASSESSMENT\n" +
                            "Film: %s (%d)\n" +
                            "Rating: %s\n\n" +
                            "--- Original Data ---\n" +
                            "  IMDb Rating: %.1f/10\n" +
                            "  Box Office: $%.0fM\n" +
                            "  Budget: $%.0fM\n" +
                            "  ROI: %.1fx\n" +
                            "  Awards: %s\n\n" +
                            "--- Quality Criteria (Fuzzy Inputs) ---\n" +
                            "  Director Quality: %.1f/10\n" +
                            "  Acting Quality: %.1f/10\n" +
                            "  Story Quality: %.1f/10\n" +
                            "  Visual Effects: %.1f/10\n" +
                            "  Cultural Impact: %.1f/10\n",
                    filmTitle, releaseYear, qualityRating,
                    imdbRating, boxOffice / 1_000_000, budget / 1_000_000, roi,
                    awardsStr,
                    directorQuality, actingQuality, storyQuality, visualEffects, culturalImpact
            );
        }
    }
}