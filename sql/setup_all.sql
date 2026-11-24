-- ============================================================================
-- CSA09 - Programming in Java | Assignment 5 (CO5)
-- Complete Database Schema & Seed Data for Tasks B2, B3, and B4
-- ============================================================================

CREATE DATABASE IF NOT EXISTS csa09_assign5
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE csa09_assign5;

-- ----------------------------------------------------------------------------
-- Task B2: Users table for SQL Injection Demonstration
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    pwd VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'STUDENT'
);

INSERT INTO users (id, pwd, role) VALUES
    ('192324214', 'Rahul@2026', 'STUDENT'),
    ('admin_transport', 'Trans#9988', 'ADMIN');

-- ----------------------------------------------------------------------------
-- Task B3: Single-Campus Bus Pass Table
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS bus_pass;
CREATE TABLE bus_pass (
    pass_id VARCHAR(20) PRIMARY KEY,
    reg_no VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    route_no INT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    INDEX idx_route_no (route_no)
);

-- ----------------------------------------------------------------------------
-- Task B4: Multi-Campus Production Bus Pass Table (12 Campuses Scale)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS campus_bus_pass;
CREATE TABLE campus_bus_pass (
    pass_id VARCHAR(25) PRIMARY KEY,
    campus_code VARCHAR(15) NOT NULL,
    reg_no VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    route_no INT NOT NULL,
    boarding_stop VARCHAR(80) NOT NULL,
    validity_date DATE NOT NULL,
    amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_campus_route (campus_code, route_no),
    INDEX idx_reg_no (reg_no)
);
