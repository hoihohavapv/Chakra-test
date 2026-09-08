/src/main/java/com/pnhub/controller/
  - VideoServlet.java
  - AuthServlet.java
  - UploadServlet.java
/src/main/webapp/
  - index.jsp
  - watch.jsp
  - upload.jsp
  - login.jsp
  - WEB-INF/web.xml
/src/main/resources/schema.sql
CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50), password_hash VARCHAR(255), role VARCHAR(10));
CREATE TABLE videos (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200), file_path VARCHAR(500), thumbnail VARCHAR(500), uploader_id INT, views INT DEFAULT 0, upload_time TIMESTAMP);
CREATE TABLE tags (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50));
CREATE TABLE video_tags (video_id INT, tag_id INT);
INSERT INTO users VALUES (1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMy.LecsaUapNxCed7NAWufiGVwgJzpa58zZZk', 'ADMIN');
INSERT INTO videos VALUES (1, 'Sample - Coastal Drone', '/assets/sample.mp4', '/assets/thumb.jpg', 1, 1337, CURRENT_TIMESTAMP);
INSERT INTO tags VALUES (1, 'drone'), (2, 'ocean'), (3, 'ruins');
INSERT INTO video_tags VALUES (1,1),(1,2),(1,3);
package com.pnhub.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet("/video")
public class VideoServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String idParam = req.getParameter("id");
        if (idParam == null) { resp.sendRedirect("index.jsp"); return; }
        int id = Integer.parseInt(idParam);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM videos WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                req.setAttribute("video", rs);
                // increment views
                try (PreparedStatement upd = conn.prepareStatement("UPDATE videos SET views=views+1 WHERE id=?")) {
                    upd.setInt(1, id); upd.executeUpdate();
                }
                RequestDispatcher rd = req.getRequestDispatcher("/watch.jsp");
                rd.forward(req, resp);
            } else {
                resp.sendError(404);
            }
        } catch (SQLException e) { throw new ServletException(e); }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // upload handler - called multipart
        String title = req.getParameter("title");
        String tags = req.getParameter("tags");
        Part filePart = req.getPart("file");
        String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
        String uploadPath = getServletContext().getRealPath("/assets/") + File.separator + fileName;
        filePart.write(uploadPath);
        String thumb = "/assets/thumb_default.jpg";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO videos (title, file_path, thumbnail, uploader_id, upload_time) VALUES (?,?,?,1,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, "/assets/" + fileName);
            ps.setString(3, thumb);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) { int vid = keys.getInt(1); // tag insert omitted for brevity but production includes full many-to-many }
        } catch (SQLException e) { throw new ServletException(e); }
        resp.sendRedirect("index.jsp");
    }
}
package com.pnhub.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/auth")
public class AuthServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        if ("login".equals(action)) {
            String user = req.getParameter("username");
            String pass = req.getParameter("password");
            try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "")) {
                PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE username=?");
                ps.setString(1, user);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && BCrypt.checkpw(pass, rs.getString(1))) {
                    HttpSession session = req.getSession();
                    session.setAttribute("user", user);
                    resp.sendRedirect("index.jsp");
                } else { resp.sendError(401); }
            } catch (SQLException e) { throw new ServletException(e); }
        } else if ("logout".equals(action)) {
            req.getSession().invalidate();
            resp.sendRedirect("index.jsp");
        }
    }
}
<%@ page import="java.sql.*" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Coastal Hub</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <style>
        body { background: #0b0d0f; color: #e0e4e8; font-family: 'Inter', sans-serif; }
        .navbar { background: #14171a !important; border-bottom: 1px solid #2a2f33; }
        .video-card { background: #1a1e22; border: none; border-radius: 12px; transition: 0.2s; }
        .video-card:hover { transform: scale(1.02); background: #22282e; }
        .thumb { width: 100%; aspect-ratio: 16/9; object-fit: cover; border-radius: 8px 8px 0 0; background: #0f1113; }
        .title { font-weight: 500; font-size: 0.95rem; margin: 8px 0 4px; }
        .views { font-size: 0.75rem; color: #8a929a; }
        .upload-btn { background: #e63946; border: none; border-radius: 40px; padding: 6px 18px; font-weight: 600; }
        .upload-btn:hover { background: #d62839; }
    </style>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-dark">
    <div class="container">
        <a class="navbar-brand fw-bold" href="#">⏻ COASTAL HUB</a>
        <div class="ms-auto d-flex gap-2">
            <a href="upload.jsp" class="btn upload-btn btn-sm">+ Upload</a>
            <% if (session.getAttribute("user") == null) { %>
                <a href="login.jsp" class="btn btn-outline-secondary btn-sm">Login</a>
            <% } else { %>
                <form action="auth" method="post" class="d-inline">
                    <input type="hidden" name="action" value="logout">
                    <button class="btn btn-outline-secondary btn-sm">Logout</button>
                </form>
            <% } %>
        </div>
    </div>
</nav>
<div class="container mt-4">
    <div class="row g-3">
        <% 
        String url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT id, title, thumbnail, views FROM videos ORDER BY upload_time DESC");
            while (rs.next()) {
        %>
        <div class="col-6 col-md-4 col-lg-3">
            <a href="video?id=<%= rs.getInt("id") %>" class="text-decoration-none text-light">
                <div class="video-card p-2">
                    <img src="<%= rs.getString("thumbnail") %>" class="thumb" alt="thumb">
                    <div class="title"><%= rs.getString("title") %></div>
                    <div class="views">👁 <%= rs.getInt("views") %></div>
                </div>
            </a>
        </div>
        <% }} catch (SQLException e) { out.print("<div class='alert alert-danger'>DB error</div>"); } %>
    </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
<%@ page import="java.sql.*" %>
<%
    ResultSet video = (ResultSet) request.getAttribute("video");
    if (video == null) { response.sendError(404); return; }
%>
<!DOCTYPE html>
<html>
<head><title><%= video.getString("title") %></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<style>
body { background: #080a0c; color: #e8ecf0; }
.player-container { max-width: 900px; margin: 40px auto; }
video { width: 100%; border-radius: 16px; background: #000; }
.meta { padding: 20px 0; }
</style>
</head>
<body>
<div class="container player-container">
    <video controls autoplay>
        <source src="<%= video.getString("file_path") %>" type="video/mp4">
    </video>
    <div class="meta">
        <h3><%= video.getString("title") %></h3>
        <span class="text-secondary">👁 <%= video.getInt("views") %> views</span>
        <a href="index.jsp" class="btn btn-outline-secondary btn-sm ms-3">← Back</a>
    </div>
</div>
</body>
</html>
<!DOCTYPE html>
<html>
<head><title>Upload</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<style>
body { background: #0b0d0f; color: #e0e4e8; }
.upload-zone { border: 2px dashed #2a2f33; border-radius: 24px; padding: 60px 20px; text-align: center; margin-top: 60px; background: #14171a; }
.upload-zone:hover { border-color: #e63946; }
</style>
</head>
<body>
<div class="container">
    <div class="upload-zone">
        <h4>⬆ Drop video file</h4>
        <form action="video" method="post" enctype="multipart/form-data" class="mt-4">
            <input type="text" name="title" class="form-control bg-dark text-light border-secondary w-50 mx-auto" placeholder="Title" required>
            <input type="text" name="tags" class="form-control bg-dark text-light border-secondary w-50 mx-auto mt-2" placeholder="Tags (comma separated)">
            <input type="file" name="file" class="form-control bg-dark text-light border-secondary w-50 mx-auto mt-2" accept="video/*" required>
            <button type="submit" class="btn upload-btn mt-3">Publish</button>
        </form>
    </div>
</div>
</body>
</html>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">
    <servlet>
        <servlet-name>VideoServlet</servlet-name>
        <servlet-class>com.pnhub.controller.VideoServlet</servlet-class>
        <multipart-config>
            <max-file-size>104857600</max-file-size>
            <max-request-size>104857600</max-request-size>
            <file-size-threshold>1048576</file-size-threshold>
        </multipart-config>
    </servlet>
    <servlet-mapping><servlet-name>VideoServlet</servlet-name><url-pattern>/video</url-pattern></servlet-mapping>
</web-app>
package com.pnhub.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import java.io.*;
import java.sql.*;
import java.nio.file.Paths;

@WebServlet("/video")
@MultipartConfig(maxFileSize = 104857600, maxRequestSize = 104857600)
public class VideoServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String idParam = req.getParameter("id");
        if (idParam == null) { resp.sendRedirect("index.jsp"); return; }
        int id = Integer.parseInt(idParam);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM videos WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                req.setAttribute("video", rs);
                try (PreparedStatement upd = conn.prepareStatement("UPDATE videos SET views=views+1 WHERE id=?")) {
                    upd.setInt(1, id); upd.executeUpdate();
                }
                RequestDispatcher rd = req.getRequestDispatcher("/watch.jsp");
                rd.forward(req, resp);
            } else {
                resp.sendError(404);
            }
        } catch (SQLException e) { throw new ServletException(e); }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String title = req.getParameter("title");
        Part filePart = req.getPart("file");
        String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
        String uploadPath = getServletContext().getRealPath("/assets/") + File.separator + fileName;
        filePart.write(uploadPath);
        String thumb = "/assets/thumb_default.jpg";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO videos (title, file_path, thumbnail, uploader_id, upload_time) VALUES (?,?,?,1,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, "/assets/" + fileName);
            ps.setString(3, thumb);
            ps.executeUpdate();
        } catch (SQLException e) { throw new ServletException(e); }
        resp.sendRedirect("index.jsp");
    }
}
    package com.pnhub.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/auth")
public class AuthServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        if ("login".equals(action)) {
            String user = req.getParameter("username");
            String pass = req.getParameter("password");
            try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "")) {
                PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE username=?");
                ps.setString(1, user);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && BCrypt.checkpw(pass, rs.getString(1))) {
                    HttpSession session = req.getSession();
                    session.setAttribute("user", user);
                    resp.sendRedirect("index.jsp");
                } else { resp.sendError(401); }
            } catch (SQLException e) { throw new ServletException(e); }
        } else if ("logout".equals(action)) {
            req.getSession().invalidate();
            resp.sendRedirect("index.jsp");
        }
    }
}
    CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50), password_hash VARCHAR(255), role VARCHAR(10));
CREATE TABLE videos (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200), file_path VARCHAR(500), thumbnail VARCHAR(500), uploader_id INT, views INT DEFAULT 0, upload_time TIMESTAMP);
CREATE TABLE tags (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50));
CREATE TABLE video_tags (video_id INT, tag_id INT);
INSERT INTO users VALUES (1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMy.LecsaUapNxCed7NAWufiGVwgJzpa58zZZk', 'ADMIN');
INSERT INTO videos VALUES (1, 'Sample - Coastal Drone', '/assets/sample.mp4', '/assets/thumb.jpg', 1, 1337, CURRENT_TIMESTAMP);
INSERT INTO tags VALUES (1, 'drone'), (2, 'ocean'), (3, 'ruins');
INSERT INTO video_tags VALUES (1,1),(1,2),(1,3);
<%@ page import="java.sql.*" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Coastal Hub</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <style>
        body { background: #0b0d0f; color: #e0e4e8; font-family: 'Inter', sans-serif; }
        .navbar { background: #14171a !important; border-bottom: 1px solid #2a2f33; }
        .video-card { background: #1a1e22; border: none; border-radius: 12px; transition: 0.2s; padding: 10px; }
        .video-card:hover { transform: scale(1.02); background: #22282e; }
        .thumb { width: 100%; aspect-ratio: 16/9; object-fit: cover; border-radius: 8px; background: #0f1113; }
        .title { font-weight: 500; font-size: 0.95rem; margin: 8px 0 4px; }
        .views { font-size: 0.75rem; color: #8a929a; }
        .upload-btn { background: #e63946; border: none; border-radius: 40px; padding: 6px 18px; font-weight: 600; color: #fff; }
        .upload-btn:hover { background: #d62839; }
        a { text-decoration: none; color: inherit; }
    </style>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-dark">
    <div class="container">
        <a class="navbar-brand fw-bold" href="#">⏻ COASTAL HUB</a>
        <div class="ms-auto d-flex gap-2">
            <a href="upload.jsp" class="btn upload-btn btn-sm">+ Upload</a>
            <% if (session.getAttribute("user") == null) { %>
                <a href="login.jsp" class="btn btn-outline-secondary btn-sm">Login</a>
            <% } else { %>
                <form action="auth" method="post" class="d-inline">
                    <input type="hidden" name="action" value="logout">
                    <button class="btn btn-outline-secondary btn-sm">Logout</button>
                </form>
            <% } %>
        </div>
    </div>
</nav>
<div class="container mt-4">
    <div class="row g-3">
        <% 
        String url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT id, title, thumbnail, views FROM videos ORDER BY upload_time DESC");
            while (rs.next()) {
        %>
        <div class="col-6 col-md-4 col-lg-3">
            <a href="video?id=<%= rs.getInt("id") %>">
                <div class="video-card">
                    <img src="<%= rs.getString("thumbnail") %>" class="thumb" alt="thumb">
                    <div class="title"><%= rs.getString("title") %></div>
                    <div class="views">👁 <%= rs.getInt("views") %></div>
                </div>
            </a>
        </div>
        <% }} catch (SQLException e) { out.print("<div class='alert alert-danger'>DB error</div>"); } %>
    </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
<%@ page import="java.sql.*" %>
<%
    ResultSet video = (ResultSet) request.getAttribute("video");
    if (video == null) { response.sendError(404); return; }
%>
<!DOCTYPE html>
<html>
<head><title><%= video.getString("title") %></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<style>
body { background: #080a0c; color: #e8ecf0; }
.player-container { max-width: 900px; margin: 40px auto; }
video { width: 100%; border-radius: 16px; background: #000; }
.meta { padding: 20px 0; }
</style>
</head>
<body>
<div class="container player-container">
    <video controls autoplay>
        <source src="<%= video.getString("file_path") %>" type="video/mp4">
    </video>
    <div class="meta">
        <h3><%= video.getString("title") %></h3>
        <span class="text-secondary">👁 <%= video.getInt("views") %> views</span>
        <a href="index.jsp" class="btn btn-outline-secondary btn-sm ms-3">← Back</a>
    </div>
</div>
</body>
</html>
<!DOCTYPE html>
<html>
<head><title>Upload</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<style>
body { background: #0b0d0f; color: #e0e4e8; }
.upload-zone { border: 2px dashed #2a2f33; border-radius: 24px; padding: 60px 20px; text-align: center; margin-top: 60px; background: #14171a; }
.upload-zone:hover { border-color: #e63946; }
.upload-btn { background: #e63946; border: none; border-radius: 40px; padding: 6px 18px; font-weight: 600; color: #fff; }
.upload-btn:hover { background: #d62839; }
</style>
</head>
<body>
<div class="container">
    <div class="upload-zone">
        <h4>⬆ Drop video file</h4>
        <form action="video" method="post" enctype="multipart/form-data" class="mt-4">
            <input type="text" name="title" class="form-control bg-dark text-light border-secondary w-50 mx-auto" placeholder="Title" required>
            <input type="file" name="file" class="form-control bg-dark text-light border-secondary w-50 mx-auto mt-2" accept="video/*" required>
            <button type="submit" class="btn upload-btn mt-3">Publish</button>
        </form>
    </div>
</div>
</body>
</html>
<!DOCTYPE html>
<html>
<head><title>Login</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<style>
body { background: #0b0d0f; color: #e0e4e8; height: 100vh; display: flex; align-items: center; justify-content: center; }
.card { background: #14171a; border: 1px solid #2a2f33; border-radius: 20px; padding: 40px; width: 360px; }
.upload-btn { background: #e63946; border: none; border-radius: 40px; padding: 8px; font-weight: 600; color: #fff; width: 100%; }
.upload-btn:hover { background: #d62839; }
</style>
</head>
<body>
<div class="card">
    <h4 class="text-center">Login</h4>
    <form action="auth" method="post">
        <input type="hidden" name="action" value="login">
        <input type="text" name="username" class="form-control bg-dark text-light border-secondary mb-2" placeholder="Username">
        <input type="password" name="password" class="form-control bg-dark text-light border-secondary mb-3" placeholder="Password">
        <button class="btn upload-btn">Enter</button>
    </form>
    <div class="mt-3 text-secondary small text-center">Default: admin / admin</div>
</div>
</body>
</html>