package app;

import fuzzy.FuzzyFilmQualitySystem;
import cbr.CaseBasedReasoning;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.List;

public class Main {

    static final String NS = "http://example.org/films#";
    static Model model;

    public static void main(String[] args) throws Exception {
        loadOntology();
        Scanner in = new Scanner(System.in);

        while (true) {
            printHeader();

            String choice;
            do {
                choice = getChoice(in);
            } while (choice.isEmpty());

            if (choice.equalsIgnoreCase("q")) {
                System.out.println("\nExiting Film Recommender. Goodbye!\n");
                break;
            }

            switch (choice) {
                case "1" -> performRecommendation(in);
                case "2" -> performQualityAssessment(in);
                case "3" -> performCBRRecommendation(in);
                default -> {
                    System.out.println("Invalid choice. Please try again.");
                    continue;
                }
            }

            System.out.println();
            System.out.print("Press ENTER to continue, or type 'Q' to quit: ");
            String next = in.nextLine().trim();
            if (next.equalsIgnoreCase("q")) {
                System.out.println("\nExiting Film Recommender. Goodbye!\n");
                break;
            }
            System.out.println();
        }

        in.close();
    }

    private static void loadOntology() throws Exception {
        System.out.println("\nLoading films...");
        model = ModelFactory.createDefaultModel();

        try (InputStream is1 = Main.class.getResourceAsStream("/ontology/films.owl")) {
            if (is1 == null) throw new IllegalArgumentException("films.owl not found");
            RDFDataMgr.read(model, is1, null, Lang.TURTLE);
        }

        try (InputStream is2 = Main.class.getResourceAsStream("/ontology/film-instances.owl")) {
            if (is2 == null) throw new IllegalArgumentException("film-instances.owl not found");
            RDFDataMgr.read(model, is2, null, Lang.TURTLE);
        }

        System.out.println("Films loaded successfully.\n");
    }

    private static void printHeader() {
        System.out.println("\nFILM RECOMMENDER SYSTEM");
        System.out.println("\nSelect function:");
        System.out.println("  [1] RECOMMEND  - Find films matching criteria");
        System.out.println("  [2] ASSESS     - Evaluate film quality (using Fuzzy Logic)");
        System.out.println("  [3] SIMILAR    - Find similar films (Case-Based Reasoning)");
        System.out.println("  [Q] QUIT       - Exit application");
    }

    private static String getChoice(Scanner in) {
        System.out.print("\nYour choice [1/2/3/Q]: ");
        String choice = in.nextLine().trim();
        return choice;
    }

    private static void performRecommendation(Scanner in) throws Exception {
        System.out.println("\nSelect recommendation mode:");
        System.out.println("  [1] STRICT  - All specified criteria must match");
        System.out.println("  [2] RANKED  - Films ranked by number of matching criteria");

        System.out.print("\nMode [1/2, default: 2]: ");
        String mode = in.nextLine().trim();
        if (mode.isEmpty()) mode = "2";

        String queryRes = mode.equals("1")
                ? "/sparql/recommend_all.rq"
                : "/sparql/recommend_any.rq";

        System.out.println("\nFILM RECOMMENDATIONS");
        System.out.println("\nEnter search criteria (leave empty to skip):");

        System.out.print("Genre (e.g., SciFi, Drama): ");
        String genre = in.nextLine().trim();

        System.out.print("Director (e.g., ChristopherNolan): ");
        String director = in.nextLine().trim();

        System.out.print("Actor (e.g., LeonardoDiCaprio): ");
        String actor = in.nextLine().trim();

        System.out.print("Language (e.g., English, Serbian): ");
        String language = in.nextLine().trim();

        System.out.print("Year from (YYYY): ");
        String yearFrom = in.nextLine().trim();

        System.out.print("Year to (YYYY): ");
        String yearTo = in.nextLine().trim();

        String sparql;
        try (InputStream is = Main.class.getResourceAsStream(queryRes)) {
            if (is == null) throw new IllegalArgumentException("SPARQL not found: " + queryRes);
            sparql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        Query query = QueryFactory.create(sparql);

        QuerySolutionMap initial = new QuerySolutionMap();
        if (!genre.isEmpty())    initial.add("G", model.createResource(NS + strip(genre)));
        if (!director.isEmpty()) initial.add("D", model.createResource(NS + strip(director)));
        if (!actor.isEmpty())    initial.add("A", model.createResource(NS + strip(actor)));
        if (!language.isEmpty()) initial.add("L", model.createResource(NS + strip(language)));
        if (!yearFrom.isEmpty()) initial.add("yearFrom", model.createTypedLiteral(yearFrom, XSDDatatype.XSDgYear));
        if (!yearTo.isEmpty())   initial.add("yearTo", model.createTypedLiteral(yearTo, XSDDatatype.XSDgYear));

        System.out.println("\nSearch Results:\n");

        QueryExecution qexec = QueryExecution.create()
                .query(query)
                .model(model)
                .substitution(initial)
                .build();

        try (qexec) {
            ResultSet rs = qexec.execSelect();
            if (!rs.hasNext()) {
                System.out.println("No films found matching the specified criteria.");
            } else {
                printFormattedResults(rs, mode.equals("2"));
            }
        }
    }

    private static void performQualityAssessment(Scanner in) {
        System.out.println("\nFILM QUALITY ASSESSMENT");
        listAllFilms();
        System.out.print("\nEnter film title: ");
        String filmTitle = in.nextLine().trim();

        if (filmTitle.isEmpty()) {
            System.out.println("Film title cannot be empty.");
            return;
        }

        try {
            FuzzyFilmQualitySystem fuzzySystem = new FuzzyFilmQualitySystem();
            FuzzyFilmQualitySystem.FilmQualityResult result = fuzzySystem.evaluateFilm(filmTitle, model);

            if (result == null) {
                System.out.println("\nFilm not found: " + filmTitle);
                System.out.println("Perhaps check the spelling and try again.");
            } else {
                System.out.println(result);
            }
        } catch (Exception e) {
            System.err.println("Error during quality assessment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void performCBRRecommendation(Scanner in) {
        System.out.println("\nSIMILAR FILMS");
        listAllFilms();

        System.out.print("\nEnter film title to find similar films: ");
        String filmTitle = in.nextLine().trim();

        if (filmTitle.isEmpty()) {
            System.out.println("Film title cannot be empty.");
            return;
        }

        int totalFilms = getTotalFilmCount();
        if (totalFilms <= 1) {
            System.out.println("Not enough films in database for comparison.");
            return;
        }

        int maxPossible = totalFilms - 1;  // exclude-ujem target film
        int topN = 5;  // default

        System.out.print("How many similar films to show? [1-" + maxPossible + ", default: 5]: ");
        String topNStr = in.nextLine().trim();

        if (!topNStr.isEmpty()) {
            try {
                topN = Integer.parseInt(topNStr);

                if (topN < 1) {
                    System.out.println("Invalid input. Using default: 5");
                    topN = 5;
                } else if (topN > maxPossible) {
                    System.out.println("Requested number too high. Using maximum: " + maxPossible);
                    topN = maxPossible;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid number format. Using default: 5");
                topN = 5;
            }
        }

        // topN is within bounds
        topN = Math.max(1, Math.min(topN, maxPossible));

        try {
            CaseBasedReasoning cbr = new CaseBasedReasoning(model);
            List<CaseBasedReasoning.SimilarFilm> similarFilms = cbr.findSimilarFilms(filmTitle, topN);

            if (similarFilms.isEmpty()) {
                System.out.println("\nFilm not found: " + filmTitle);
                return;
            }

            System.out.println("\nSimilar Films to \"" + filmTitle + "\"\n");

            System.out.printf("%-5s %-40s %-8s %-8s %-30s %-30s%n",
                    "Rank", "Title", "Year", "IMDb", "Director", "Genres");
            System.out.println("=".repeat(130));

            int rank = 1;
            for (CaseBasedReasoning.SimilarFilm film : similarFilms) {
                String genres = String.join(", ", film.getGenres());
                System.out.printf("%-5d %-40s %-8d %-8.1f %-30s %-30s%n",
                        rank++,
                        truncate(film.getTitle(), 40),
                        film.getYear(),
                        film.getImdbRating(),
                        truncate(film.getDirector(), 30),
                        truncate(genres, 30));

                System.out.printf("      Similarity Score: %.0f points%n%n", film.getScore());
            }

        } catch (Exception e) {
            System.err.println("Error during CBR recommendation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int getTotalFilmCount() {
        String sparql =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT (COUNT(?film) AS ?count) WHERE { ?film rdf:type :Film }";

        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            if (rs.hasNext()) {
                return rs.next().getLiteral("count").getInt();
            }
        }
        return 0;
    }

    private static void printFormattedResults(ResultSet rs, boolean showScore) {
        if (showScore) {
            System.out.printf("%-40s %-8s %-30s %-30s %-6s%n",
                    "Title", "Year", "Director", "Genres", "Score");
            System.out.println("=".repeat(120));
        } else {
            System.out.printf("%-40s %-8s %-30s %-30s%n",
                    "Title", "Year", "Director", "Genres");
            System.out.println("=".repeat(110));
        }

        while (rs.hasNext()) {
            QuerySolution sol = rs.next();

            String title = sol.contains("title") ? sol.getLiteral("title").getString() : "N/A";
            String year = sol.contains("year") ? extractYear(sol.getLiteral("year").getString()) : "N/A";
            String director = sol.contains("director") ? sol.getLiteral("director").getString() : "N/A";

            String genres = "N/A";
            if (sol.contains("genres")) {
                String temp = sol.getLiteral("genres").getString();
                if (!temp.isEmpty()) {
                    genres = temp;
                }
            }

            if (showScore) {
                int score = sol.contains("score") ? sol.getLiteral("score").getInt() : 0;
                System.out.printf("%-40s %-8s %-30s %-30s %-6d%n",
                        truncate(title, 40), year, truncate(director, 30), truncate(genres, 30), score);
            } else {
                System.out.printf("%-40s %-8s %-30s %-30s%n",
                        truncate(title, 40), year, truncate(director, 30), truncate(genres, 30));
            }
        }

        System.out.println();
        System.out.flush();
    }

    private static String extractYear(String yearLiteral) {
        if (yearLiteral.contains("^^")) {
            return yearLiteral.substring(0, yearLiteral.indexOf("^^")).replace("\"", "");
        }
        return yearLiteral.replace("\"", "");
    }

    private static String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private static String strip(String s) {
        return s.replaceAll("[\\s,'']", "");
    }

    private static void listAllFilms() {
        String sparql =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?title WHERE { ?film rdf:type :Film ; :title ?title } ORDER BY ?title";

        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();
            System.out.println("\nAvailable films:");
            int count = 0;
            while (rs.hasNext()) {
                System.out.println("  - " + rs.next().getLiteral("title").getString());
                count++;
            }
            System.out.println("\nTotal: " + count + " films");
        }
    }
}