import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import jakarta.servlet.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;


@WebServlet(urlPatterns = {"/DBServlet"})
public class DBServlet extends HttpServlet {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/";
    private static final String USER = "root";
    private static final String PASS = "root";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        printPage(response, null, null, "db"); 
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String sql = request.getParameter("sql");
        String db = request.getParameter("db");
        if (db == null || db.isEmpty()) db = "db";

        String resultHtml = null;
        String error = null;
        StringBuilder tableList = new StringBuilder();

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            stmt.execute("USE " + db);

            boolean isResultSet = stmt.execute(sql);
            if (isResultSet) {
                ResultSet rs = stmt.getResultSet();
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();

                StringBuilder result = new StringBuilder();
                result.append("<table class='table table-bordered'><thead><tr>");
                for (int i = 1; i <= columns; i++) {
                    result.append("<th>").append(meta.getColumnName(i)).append("</th>");
                }
                result.append("</tr></thead><tbody>");

                while (rs.next()) {
                    result.append("<tr>");
                    for (int i = 1; i <= columns; i++) {
                        result.append("<td>").append(rs.getString(i)).append("</td>");
                    }
                    result.append("</tr>");
                }
                result.append("</tbody></table>");
                resultHtml = result.toString();
            } else {
                int updateCount = stmt.getUpdateCount();
                resultHtml = "Query OK, " + updateCount + " rows affected.";
            }

            ResultSet rs2 = stmt.executeQuery("SHOW TABLES");
            while (rs2.next()) {
                tableList.append("<li class='list-group-item'>").append(rs2.getString(1)).append("</li>");
            }

        } catch (SQLException e) {
            error = e.getMessage();
        }

        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setContentType("application/json");
            PrintWriter out = response.getWriter();
            out.print("{");
            out.print("\"result\": " + toJson(resultHtml) + ",");
            out.print("\"error\": " + toJson(error) + ",");
            out.print("\"tablesHtml\": " + toJson(tableList.toString()));
            out.print("}");
        } else {
            printPage(response, resultHtml, error, db);
        }
    }

    private void printPage(HttpServletResponse response, String result, String error, String db) throws IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        // Load table list
        StringBuilder tableList = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            stmt.execute("USE " + db);
            ResultSet rs = stmt.executeQuery("SHOW TABLES");
            while (rs.next()) {
                tableList.append("<li class='list-group-item'>").append(rs.getString(1)).append("</li>");
            }
        } catch (SQLException e) {
            tableList.append("<li class='list-group-item text-danger'>Error loading tables</li>");
        }

        out.println("<!DOCTYPE html><html lang='en'><head>");
        out.println("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        out.println("<title>SQL Compiler</title>");
        out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
        out.println("</head><body><div class='container-fluid'><div class='row'>");
        out.println("<div class='col-md-3 bg-light p-3'>");
        out.println("<h5>📋 Tables in `" + db + "`</h5>");
        out.println("<ul id='tableList' class='list-group'>" + tableList.toString() + "</ul>");
        out.println("</div>");
        out.println("<div class='col-md-9 p-4'>");
        out.println("<h2>🛠️ SQL Compiler</h2>");
        out.println("<form id='sqlForm'>");
        out.println("<div class='mb-3'><label>Select DB: </label>");
        out.println("<input type='text' class='form-control' id='db' name='db' value='" + db + "' required></div>");
        out.println("<div class='mb-3'><textarea name='sql' id='sqlInput' class='form-control' rows='6' placeholder='SELECT * FROM tablename'></textarea></div>");
        out.println("<button type='submit' class='btn btn-primary'>Execute</button></form>");

        out.println("<div id='ajaxResult' class='mt-4'></div>");
        out.println("<div id='ajaxError' class='mt-4 text-danger'></div>");
        out.println("</div></div></div>");

        out.println("<script>");
        out.println("document.getElementById('sqlForm').addEventListener('submit', function(e) {");
        out.println("e.preventDefault();");
        out.println("const sql = document.getElementById('sqlInput').value;");
        out.println("const db = document.getElementById('db').value;");
        out.println("fetch('DBServlet', {");
        out.println("method: 'POST',");
        out.println("headers: {'Content-Type': 'application/x-www-form-urlencoded','X-Requested-With': 'XMLHttpRequest'},");
        out.println("body: new URLSearchParams({ sql: sql, db: db })");
        out.println("})");
        out.println(".then(response => response.json())");
        out.println(".then(data => {");
        out.println("document.getElementById('ajaxResult').innerHTML = data.result || '';");
        out.println("document.getElementById('ajaxError').innerHTML = data.error ? `<div class='alert alert-danger'><pre>${data.error}</pre></div>` : '';");
        out.println("document.getElementById('tableList').innerHTML = data.tablesHtml;");
        out.println("});");
        out.println("});");
        out.println("</script>");

        out.println("</body></html>");
    }

    private String toJson(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }

    public void init() throws ServletException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ServletException("JDBC Driver not found.", e);
        }
    }
}