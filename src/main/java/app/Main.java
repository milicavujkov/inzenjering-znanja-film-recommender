package app;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {

    static final String NS = "http://example.org/films#";
    static Model model;

    public static void main(String[] args) throws Exception {
        loadOntology();
        Scanner in = new Scanner(System.in);

        while (true) {
            printHeader();
            String mode = getMode(in);

            if (mode.equalsIgnoreCase("q")) {
                System.out.println("\nExiting Film Recommender. Goodbye!\n");
                break;
            }

            performSearch(in, mode);

            System.out.println();
            System.out.print("Press ENTER to search again, or type 'Q' to quit: ");
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
        System.out.println("\nSelect recommendation mode:");
        System.out.println("  [1] STRICT  - All specified criteria must match");
        System.out.println("  [2] RANKED  - Films ranked by number of matching criteria");
        System.out.println("  [Q] QUIT    - Exit application");
    }

    private static String getMode(Scanner in) {
        System.out.print("\nYour choice [1/2/Q, default: 2]: ");
        String mode = in.nextLine().trim();
        if (mode.isEmpty()) mode = "2";

        if (!mode.equals("1") && !mode.equals("2") && !mode.equalsIgnoreCase("q")) {
            System.out.println("Invalid choice. Defaulting to RANKED mode.");
            return "2";
        }

        return mode;
    }

    private static void performSearch(Scanner in, String mode) throws Exception {
        String queryRes = mode.equals("1")
                ? "/sparql/recommend_all.rq"
                : "/sparql/recommend_any.rq";

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

        // Load SPARQL query
        String sparql;
        try (InputStream is = Main.class.getResourceAsStream(queryRes)) {
            if (is == null) throw new IllegalArgumentException("SPARQL not found: " + queryRes);
            sparql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        Query query = QueryFactory.create(sparql);

        // Bind only non-empty user inputs
        QuerySolutionMap initial = new QuerySolutionMap();
        if (!genre.isEmpty())    initial.add("G", model.createResource(NS + strip(genre)));
        if (!director.isEmpty()) initial.add("D", model.createResource(NS + strip(director)));
        if (!actor.isEmpty())    initial.add("A", model.createResource(NS + strip(actor)));
        if (!language.isEmpty()) initial.add("L", model.createResource(NS + strip(language)));
        if (!yearFrom.isEmpty()) initial.add("yearFrom", model.createTypedLiteral(yearFrom, XSDDatatype.XSDgYear));
        if (!yearTo.isEmpty())   initial.add("yearTo", model.createTypedLiteral(yearTo, XSDDatatype.XSDgYear));

        // Execute query
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
        System.out.print("\n");
    }

    private static void printFormattedResults(ResultSet rs, boolean showScore) {
        // Header
        if (showScore) {
            System.out.printf("%-40s %-8s %-30s %-30s %-6s%n",
                    "Title", "Year", "Director", "Genres", "Score");
            System.out.println("=".repeat(120));
        } else {
            System.out.printf("%-40s %-8s %-30s %-30s%n",
                    "Title", "Year", "Director", "Genres");
            System.out.println("=".repeat(110));
        }

        // Results
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
}