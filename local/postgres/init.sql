-- Local only: one database and owner per service.
CREATE USER exam_analysis WITH PASSWORD 'exam_analysis';
CREATE DATABASE exam_analysis OWNER exam_analysis;
