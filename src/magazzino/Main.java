package magazzino;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class Main {
    // Configurazione del database
    private static final String DB_URL = "jdbc:mysql://localhost:3306/vet_magazzino";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    public static void main(String[] args) throws IOException {
        // Inizializzazione del server HTTP
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("Server avviato sulla porta 8080");

        // Handlers per le varie richieste
        server.createContext("/", new IndexHandler());
        server.createContext("/inserisciProdotto", new InserisciProdottoHandler());
        server.createContext("/riepilogoMagazzino", new RiepilogoMagazzinoHandler());
        server.createContext("/prelevaProdotto", new PrelevaProdottoHandler());
        server.createContext("/eliminaProdotto", new EliminaProdottoHandler());
        server.createContext("/prodottiInScadenza", new ProdottiInScadenzaHandler());
        server.createContext("/prodottiDaOrdinare", new ProdottiDaOrdinareHandler());
        // Servire file statici (CSS, JS, immagini)
        server.createContext("/css/", new StaticFileHandler());
        server.createContext("/js/", new StaticFileHandler());
        server.createContext("/img/", new StaticFileHandler());

        server.setExecutor(null); // Crea un default executor
        server.start();
    }

    // Handler per la pagina index.html
    static class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Imposta l'header Content-Type
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");

            // Legge il contenuto del file index.html
            InputStream is = getClass().getResourceAsStream("index.html");
            if (is == null) {
                String error = "File index.html non trovato";
                exchange.sendResponseHeaders(404, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
                return;
            }

            // Legge il file e lo invia come risposta
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }

    // Handler per inserire un nuovo prodotto nel magazzino
    static class InserisciProdottoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Legge i dati inviati dal client
                String postData = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parsePostData(postData);

                String nomeProdotto = params.get("nomeProdotto");
                int quantita = Integer.parseInt(params.get("quantita"));
                String scadenza = params.get("scadenza");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "INSERT INTO magazzino (nome, quantita, scadenza) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, nomeProdotto);
                        stmt.setInt(2, quantita);
                        if (scadenza != null && !scadenza.isEmpty()) {
                            stmt.setDate(3, Date.valueOf(scadenza));
                        } else {
                            stmt.setNull(3, Types.DATE);
                        }
                        stmt.executeUpdate();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    String response = "Errore nell'inserimento del prodotto";
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }

                // Invia una risposta di successo
                String response = "Prodotto inserito con successo";
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Metodo non supportato
            }
        }

        // Funzione per parsare i dati POST
        private Map<String, String> parsePostData(String postData) throws UnsupportedEncodingException {
            Map<String, String> params = new HashMap<>();
            String[] pairs = postData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], "UTF-8");
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
                params.put(key, value);
            }
            return params;
        }
    }

    // Handler per recuperare il riepilogo del magazzino
    static class RiepilogoMagazzinoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

                StringBuilder jsonResponse = new StringBuilder();
                jsonResponse.append("[");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "SELECT id, nome, quantita, scadenza FROM magazzino";
                    try (Statement stmt = conn.createStatement()) {
                        ResultSet rs = stmt.executeQuery(sql);
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) {
                                jsonResponse.append(",");
                            }
                            first = false;

                            jsonResponse.append("{");
                            jsonResponse.append("\"id\":").append(rs.getInt("id")).append(",");
                            jsonResponse.append("\"nome\":\"").append(escapeJson(rs.getString("nome"))).append("\",");
                            jsonResponse.append("\"quantita\":").append(rs.getInt("quantita")).append(",");
                            Date scadenza = rs.getDate("scadenza");
                            if (scadenza != null) {
                                jsonResponse.append("\"scadenza\":\"").append(scadenza.toString()).append("\"");
                            } else {
                                jsonResponse.append("\"scadenza\":null");
                            }
                            jsonResponse.append("}");
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }

                jsonResponse.append("]");

                byte[] responseBytes = jsonResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private String escapeJson(String str) {
            if (str == null) {
                return "";
            }
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\b", "\\b")
                      .replace("\f", "\\f")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t");
        }
    }

    // Handler per prelevare un prodotto
    static class PrelevaProdottoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String postData = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parsePostData(postData);

                int idProdotto = Integer.parseInt(params.get("id"));
                int quantitaDaPrelevare = Integer.parseInt(params.get("quantita"));

                boolean prelievoRiuscito = false;
                String messaggioErrore = "";

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    conn.setAutoCommit(false);

                    String sqlSelect = "SELECT nome, quantita FROM magazzino WHERE id = ?";
                    int quantitaDisponibile = 0;
                    String nomeProdotto = "";
                    try (PreparedStatement stmtSelect = conn.prepareStatement(sqlSelect)) {
                        stmtSelect.setInt(1, idProdotto);
                        ResultSet rs = stmtSelect.executeQuery();
                        if (rs.next()) {
                            quantitaDisponibile = rs.getInt("quantita");
                            nomeProdotto = rs.getString("nome");
                        } else {
                            messaggioErrore = "Prodotto non trovato.";
                        }
                    }

                    if (quantitaDaPrelevare <= quantitaDisponibile) {
                        int nuovaQuantita = quantitaDisponibile - quantitaDaPrelevare;

                        if (nuovaQuantita > 0) {
                            String sqlUpdate = "UPDATE magazzino SET quantita = ? WHERE id = ?";
                            try (PreparedStatement stmtUpdate = conn.prepareStatement(sqlUpdate)) {
                                stmtUpdate.setInt(1, nuovaQuantita);
                                stmtUpdate.setInt(2, idProdotto);
                                int rowsAffected = stmtUpdate.executeUpdate();
                                if (rowsAffected > 0) {
                                    prelievoRiuscito = true;
                                    conn.commit();
                                } else {
                                    messaggioErrore = "Errore nell'aggiornamento del prodotto.";
                                    conn.rollback();
                                }
                            }
                        } else {
                            String sqlDelete = "DELETE FROM magazzino WHERE id = ?";
                            String sqlInsert = "INSERT INTO da_ordinare (id, nome, quantita) VALUES (?, ?, ?)";

                            try (PreparedStatement stmtDelete = conn.prepareStatement(sqlDelete);
                                 PreparedStatement stmtInsert = conn.prepareStatement(sqlInsert)) {

                                stmtDelete.setInt(1, idProdotto);
                                int deleteRows = stmtDelete.executeUpdate();

                                if (deleteRows > 0) {
                                    stmtInsert.setInt(1, idProdotto);
                                    stmtInsert.setString(2, nomeProdotto);
                                    stmtInsert.setInt(3, 0);
                                    int insertRows = stmtInsert.executeUpdate();

                                    if (insertRows > 0) {
                                        prelievoRiuscito = true;
                                        conn.commit();
                                    } else {
                                        messaggioErrore = "Errore nell'inserimento in da_ordinare.";
                                        conn.rollback();
                                    }
                                } else {
                                    messaggioErrore = "Errore nella rimozione dal magazzino.";
                                    conn.rollback();
                                }
                            }
                        }
                    } else {
                        messaggioErrore = "Quantità richiesta non disponibile.";
                        conn.rollback();
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    messaggioErrore = "Errore nel server.";
                }

                if (prelievoRiuscito) {
                    String response = "Prodotto prelevato con successo";
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                } else {
                    String response = messaggioErrore;
                    exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private Map<String, String> parsePostData(String postData) throws UnsupportedEncodingException {
            Map<String, String> params = new HashMap<>();
            String[] pairs = postData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], "UTF-8");
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
                params.put(key, value);
            }
            return params;
        }
    }

    // Handler per eliminare un prodotto
    static class EliminaProdottoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String postData = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parsePostData(postData);

                int idProdotto = Integer.parseInt(params.get("id"));

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "DELETE FROM magazzino WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, idProdotto);
                        int rowsAffected = stmt.executeUpdate();
                        if (rowsAffected == 0) {
                            String response = "Prodotto non trovato";
                            exchange.sendResponseHeaders(404, response.length());
                            OutputStream os = exchange.getResponseBody();
                            os.write(response.getBytes());
                            os.close();
                            return;
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    String response = "Errore nell'eliminazione del prodotto";
                    exchange.sendResponseHeaders(500, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }

                String response = "Prodotto eliminato con successo";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private Map<String, String> parsePostData(String postData) throws UnsupportedEncodingException {
            Map<String, String> params = new HashMap<>();
            String[] pairs = postData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], "UTF-8");
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
                params.put(key, value);
            }
            return params;
        }
    }

    // Handler per recuperare i prodotti in scadenza
    static class ProdottiInScadenzaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                int giorni = Integer.parseInt(params.getOrDefault("giorni", "7"));

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

                StringBuilder jsonResponse = new StringBuilder();
                jsonResponse.append("[");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "SELECT id, nome, quantita, scadenza FROM magazzino WHERE scadenza <= DATE_ADD(CURDATE(), INTERVAL ? DAY)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, giorni);
                        ResultSet rs = stmt.executeQuery();
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) {
                                jsonResponse.append(",");
                            }
                            first = false;

                            jsonResponse.append("{");
                            jsonResponse.append("\"id\":").append(rs.getInt("id")).append(",");
                            jsonResponse.append("\"nome\":\"").append(escapeJson(rs.getString("nome"))).append("\",");
                            jsonResponse.append("\"quantita\":").append(rs.getInt("quantita")).append(",");
                            Date scadenza = rs.getDate("scadenza");
                            if (scadenza != null) {
                                jsonResponse.append("\"scadenza\":\"").append(scadenza.toString()).append("\"");
                            } else {
                                jsonResponse.append("\"scadenza\":null");
                            }
                            jsonResponse.append("}");
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }

                jsonResponse.append("]");

                byte[] responseBytes = jsonResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private Map<String, String> parseQuery(String query) throws UnsupportedEncodingException {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isEmpty()) {
                return params;
            }
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], "UTF-8");
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
                params.put(key, value);
            }
            return params;
        }

        private String escapeJson(String str) {
            if (str == null) {
                return "";
            }
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\b", "\\b")
                      .replace("\f", "\\f")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t");
        }
    }

    // Handler per recuperare i prodotti da ordinare
    static class ProdottiDaOrdinareHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

                StringBuilder jsonResponse = new StringBuilder();
                jsonResponse.append("[");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "SELECT id, nome, quantita FROM da_ordinare";
                    try (Statement stmt = conn.createStatement()) {
                        ResultSet rs = stmt.executeQuery(sql);
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) {
                                jsonResponse.append(",");
                            }
                            first = false;

                            jsonResponse.append("{");
                            jsonResponse.append("\"id\":").append(rs.getInt("id")).append(",");
                            jsonResponse.append("\"nome\":\"").append(escapeJson(rs.getString("nome"))).append("\",");
                            jsonResponse.append("\"quantita\":").append(rs.getInt("quantita"));
                            jsonResponse.append("}");
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }

                jsonResponse.append("]");

                byte[] responseBytes = jsonResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private String escapeJson(String str) {
            if (str == null) {
                return "";
            }
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\b", "\\b")
                      .replace("\f", "\\f")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t");
        }
    }

    // Handler per servire file statici (CSS, JS, immagini)
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String filePath = exchange.getRequestURI().getPath();
            InputStream is = getClass().getResourceAsStream("/magazzino" + filePath);
            if (is == null) {
                String error = "File non trovato";
                exchange.sendResponseHeaders(404, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
                return;
            }

            // Determina il Content-Type
            String contentType;
            if (filePath.endsWith(".css")) {
                contentType = "text/css";
            } else if (filePath.endsWith(".js")) {
                contentType = "application/javascript";
            } else if (filePath.endsWith(".png")) {
                contentType = "image/png";
            } else if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (filePath.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else {
                contentType = "application/octet-stream";
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);

            byte[] fileBytes = is.readAllBytes();
            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }
    }
}

