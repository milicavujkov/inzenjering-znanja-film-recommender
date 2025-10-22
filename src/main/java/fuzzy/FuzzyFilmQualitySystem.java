package fuzzy;

import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;

public class FuzzyFilmQualitySystem {

    private FIS fis;
    private FunctionBlock fb;

    public FuzzyFilmQualitySystem() {
        // load FCL file
        String fclPath = "src/main/java/fuzzy/film_quality.fcl";
        fis = FIS.load(fclPath, true);

        if (fis == null) {
            throw new RuntimeException("Cannot load FCL file: film_quality.fcl");
        }

        fb = fis.getFunctionBlock("filmQuality");
    }

    public FilmQualityResult evaluateFilm(String filmTitle, Model model) {
        // extract film data from ontology
        FilmData data = extractFilmData(filmTitle, model);

        if (data == null) {
            return null;
        }

        // calculate fuzzy inputs
        double overallRating = data.imdbRating;
        double boxOfficeSuccess = calculateBoxOfficeSuccess(data.boxOffice, data.budget);
        double awardsCount = normalizeAwardsCount(data.awardsCount);
        double culturalAge = calculateCulturalAge(data.releaseYear);
        double criticalAcclaim = calculateCriticalAcclaim(data.imdbRating, data.awardsCount);

        // set fuzzy inputs
        fb.setVariable("overallRating", overallRating);
        fb.setVariable("boxOfficeSuccess", boxOfficeSuccess);
        fb.setVariable("awardsCount", awardsCount);
        fb.setVariable("culturalAge", culturalAge);
        fb.setVariable("criticalAcclaim", criticalAcclaim);

        // evaluate
        fb.evaluate();

        // get output
        double quality = fb.getVariable("quality").getValue();

        return new FilmQualityResult(filmTitle, quality, data,
                overallRating, boxOfficeSuccess, awardsCount, culturalAge, criticalAcclaim);
    }


    private FilmData extractFilmData(String filmTitle, Model model) {
        // get basic film data
        String sparql1 =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?imdb ?boxOffice ?budget ?year " +
                        "WHERE { " +
                        "  ?film rdf:type :Film ; " +
                        "        :title ?title ; " +
                        "        :imdbRating ?imdb . " +
                        "  OPTIONAL { ?film :boxOfficeUSD ?boxOffice } " +
                        "  OPTIONAL { ?film :budgetUSD ?budget } " +
                        "  OPTIONAL { ?film :releaseYear ?year } " +
                        "  FILTER(LCASE(STR(?title)) = LCASE(\"" + filmTitle + "\")) " +
                        "} LIMIT 1";

        // defaults
        double imdb = 0, boxOffice = 0, budget = 1;
        int year = 2000;

        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql1))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            if (!rs.hasNext()) {
                return null; // film not found
            }

            QuerySolution sol = rs.next();
            imdb = sol.getLiteral("imdb").getDouble();
            boxOffice = sol.contains("boxOffice") ? sol.getLiteral("boxOffice").getDouble() : 0;
            budget = sol.contains("budget") ? sol.getLiteral("budget").getDouble() : 1;

            if (sol.contains("year")) {
                String yearStr = sol.getLiteral("year").getString();
                year = Integer.parseInt(yearStr.substring(0, 4));
            }
        }

        // count awards separately
        String sparql2 =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT (COUNT(?award) AS ?awards) " +
                        "WHERE { " +
                        "  ?film rdf:type :Film ; " +
                        "        :title ?title . " +
                        "  OPTIONAL { ?film :wonAward ?award } " +
                        "  FILTER(LCASE(STR(?title)) = LCASE(\"" + filmTitle + "\")) " +
                        "}";

        int awards = 0;
        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql2))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            if (rs.hasNext()) {
                awards = rs.next().getLiteral("awards").getInt();
            }
        }

        return new FilmData(imdb, boxOffice, budget, year, awards);
    }

    private double calculateBoxOfficeSuccess(double boxOffice, double budget) {
        if (budget == 0) return 5.0; // neutral if no budget data
        double ratio = boxOffice / budget;
        // normalize to 0-10 scale
        if (ratio < 0.5) return 0;
        if (ratio > 10) return 10;
        return ratio;
    }

    private double normalizeAwardsCount(int awards) {
        // normalize awards count to 0-10 scale
        if (awards == 0) return 0;
        if (awards >= 10) return 10;
        return awards;
    }

    private double calculateCulturalAge(int year) {
        int currentYear = 2025;
        int age = currentYear - year;
        // normalize to 0-100 scale
        if (age > 100) return 100;
        return age;
    }

    private double calculateCriticalAcclaim(double imdb, int awards) {
        // combine IMDb rating and awards
        double imdbComponent = imdb; // already 0-10
        double awardsComponent = Math.min(awards * 0.5, 2); // max +2 for awards
        return Math.min(imdbComponent + awardsComponent, 10);
    }

    // inner classes for data structures
    private static class FilmData {
        double imdbRating;
        double boxOffice;
        double budget;
        int releaseYear;
        int awardsCount;

        FilmData(double imdb, double boxOffice, double budget, int year, int awards) {
            this.imdbRating = imdb;
            this.boxOffice = boxOffice;
            this.budget = budget;
            this.releaseYear = year;
            this.awardsCount = awards;
        }
    }

    public static class FilmQualityResult {
        public String filmTitle;
        public double qualityScore;
        public String qualityRating;

        // input values
        public double overallRating;
        public double boxOfficeSuccess;
        public double awardsCount;
        public double culturalAge;
        public double criticalAcclaim;

        // original data
        public double imdbRating;
        public double boxOffice;
        public double budget;
        public int releaseYear;
        public int awards;

        FilmQualityResult(String title, double quality, FilmData data,
                          double overall, double boxOffice, double awards, double age, double acclaim) {
            this.filmTitle = title;
            this.qualityScore = quality;
            this.qualityRating = getRating(quality);

            this.overallRating = overall;
            this.boxOfficeSuccess = boxOffice;
            this.awardsCount = awards;
            this.culturalAge = age;
            this.criticalAcclaim = acclaim;

            this.imdbRating = data.imdbRating;
            this.boxOffice = data.boxOffice;
            this.budget = data.budget;
            this.releaseYear = data.releaseYear;
            this.awards = data.awardsCount;
        }

        private String getRating(double quality) {
            if (quality < 40) return "POOR";
            if (quality < 70) return "GOOD";
            return "EXCELLENT";
        }

        @Override
        public String toString() {
            return String.format(
                            "Film: %s\n" +
                            "Quality Score: %.2f/100\n" +
                            "Rating: %s\n\n" +
                            "Original Data:\n" +
                            "  IMDb Rating: %.1f/10\n" +
                            "  Box Office: $%.0fM\n" +
                            "  Budget: $%.0fM\n" +
                            "  Release Year: %d\n" +
                            "  Awards Won: %d\n\n" +
                            "Fuzzy Inputs:\n" +
                            "  Overall Rating: %.1f/10\n" +
                            "  Box Office Success: %.1f/10\n" +
                            "  Awards Count: %.0f/10\n" +
                            "  Cultural Age: %.0f years\n" +
                            "  Critical Acclaim: %.1f/10\n",
                    filmTitle, qualityScore, qualityRating,
                    imdbRating, boxOffice / 1_000_000, budget / 1_000_000,
                    releaseYear, awards,
                    overallRating, boxOfficeSuccess, awardsCount, culturalAge, criticalAcclaim
            );
        }
    }
}