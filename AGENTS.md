## Project Overview

This project implements an AI Agent platform using Java 21 and Hexagonal Architecture.

The primary goal is to build a clean, maintainable, production-ready system demonstrating Senior/Lead level engineering practices.

Every change must improve maintainability and never reduce code quality.

The project should be suitable as a portfolio project.

---

# General Principles

Always prefer

- readability
- simplicity
- maintainability
- explicitness

over

- clever code
- unnecessary abstractions
- premature optimizations

Code should be understandable by another developer without additional explanation.

---

# Development Philosophy

Before implementing anything:

1. Understand the problem.
2. Analyze the existing code.
3. Reuse existing abstractions whenever possible.
4. Minimize duplication.
5. Implement.
6. Verify.
7. Refactor if necessary.

Never immediately start generating code.

Always think first.

---

# Technology Stack

Language

- Java 21
- Vert.x 5

Architecture

- Hexagonal Architecture

Database

- PostgreSQL

Migration

- Liquibase

Containerization

- Docker
- Docker Compose

Frontend

- React
- TypeScript

Communication

- REST API
- WebSocket

Testing

- JUnit 5
- Mockito
- Testcontainers

Documentation

- OpenAPI / Swagger when API documentation is introduced or changed
- The current implemented project may not have OpenAPI / Swagger wiring yet; do not assume it exists without checking the codebase first

---

# Architecture Rules

The project uses Hexagonal Architecture.

Business logic must never depend on frameworks.

Current project constraint:

- The domain layer must remain framework-free.
- The application layer currently uses Vert.x `Future` as the async boundary type.
- Do not introduce additional framework dependencies into domain/application.
- If replacing Vert.x `Future` in the application layer, use an explicit async abstraction consistently across ports and use cases.

Allowed dependency direction:

Infrastructure

↓

Application

↓

Domain

Never reverse this dependency.

The domain layer must not know about:

- REST
- JDBC
- PostgreSQL
- Spring Boot
- Jackson
- HTTP
- Docker

The domain should contain only business logic.

---

# Package Naming

Use meaningful package names.

Example

domain

application

infrastructure

web

configuration

Do not use package names:

utils

helpers

common

misc

manager

processor

unless they truly describe their purpose.

---

# SOLID

Every implementation should follow SOLID principles.

Especially:

Single Responsibility Principle

Prefer composition over inheritance.

Avoid large God classes.

---

# Class Size

Recommended maximum:

300-400 lines.

Methods:

prefer under 30 lines.

If a class becomes too large, split responsibilities.

---

# Method Design

Methods should perform one logical action.

Avoid deeply nested code.

Prefer guard clauses.

Avoid boolean flags that radically change behavior.

---

# Naming

Names should explain intent.

Good

calculatePrice()

Bad

process()

execute()

handle()

unless context makes them obvious.

---

# JavaDoc

Every public class

Every public interface

Every public method

must contain JavaDoc.

JavaDoc language:

Russian.

Describe

- purpose
- parameters
- return values
- exceptions if relevant

Avoid obvious comments.

---

# Comments

Do not explain WHAT code does.

Explain WHY.

Avoid redundant comments.

---

# Exceptions

Use centralized exception handling.

Never swallow exceptions.

Never use

printStackTrace()

Never catch Exception unless absolutely necessary.

Allowed exception boundary:

- `catch (Exception e)` is acceptable at external adapter boundaries where third-party/framework code can throw unexpected runtime errors.
- Such catches must map errors to stable API responses or startup failures.
- Such catches must never swallow the exception silently.
- Such catches must never expose stack traces or sensitive details to clients.

Use domain-specific exceptions.

---

# Logging

Use SLF4J.

Rules

DEBUG

- execution details

INFO

- important business events

WARN

- recoverable problems

ERROR

- unexpected failures

Never log

passwords

tokens

API keys

personal data

---

# Validation

Validate input as early as possible.

Business validation belongs inside the application/domain.

HTTP validation belongs to adapters.

---

# Immutability

Prefer immutable objects.

Prefer records whenever appropriate.

Minimize mutable state.

---

# DTO

DTOs belong only outside the domain.

Never leak persistence entities outside infrastructure.

---

# Database

All schema changes

only through Liquibase.

Never modify schema manually.

Every migration must be:

- idempotent
- readable
- reversible whenever practical

---

# SQL

Prefer readable SQL.

Avoid SELECT *

Use indexes when necessary.

---

# Transactions

Keep transactions small.

Avoid long-running transactions.

---

# REST

REST endpoints should

- be consistent
- return meaningful status codes
- never expose internal exceptions

---

# WebSocket

WebSocket endpoints should

- keep message contracts explicit and stable
- validate client messages before processing
- enforce configured message size limits
- isolate client subscriptions by client identity
- never expose internal exceptions

---

# JSON

Use stable API contracts.

Avoid breaking changes.

---

# Testing

Every new business logic

must have tests.

Prefer:

Unit tests

before

Integration tests.

Use Testcontainers for integration testing.

Avoid sleeping in tests.

Tests must be deterministic.

---

# Frontend

Frontend should resemble ChatGPT.

Requirements

Clean

Minimalistic

Responsive

Modern

Avoid unnecessary libraries.

Business logic should remain in backend.

---

# AI Agent Rules

The AI Agent should be modular.

Every tool should be isolated.

Avoid hardcoded prompts.

Prompt templates should be configurable when real LLM integration is introduced.

LLM integration should be abstracted behind interfaces.

Current implementation note:

- The project currently uses a mock AI client.
- Do not assume real LLM integration or prompt template storage exists without checking the codebase.

---

# Configuration

Configuration belongs outside code.

Never hardcode

URLs

API Keys

Passwords

Secrets

---

# Dependencies

Before adding a dependency:

Ask:

Can existing libraries solve this?

Prefer fewer dependencies.

---

# Refactoring

Whenever modifying existing code:

Leave it cleaner than before.

---

# Performance

Optimize only after correctness.

Avoid premature optimization.

Measure before optimizing.

---

# Security

Validate every external input.

Escape output where needed.

Never trust user input.

Never expose stack traces.

---

# Git

One task

One commit.

Commit message should clearly explain intent.

Agents must not create commits unless the user explicitly asks for a commit.

---

# Code Review Checklist

Before finishing:

✓ Compiles

✓ Tests pass

✓ JavaDoc updated

✓ No duplicated code

✓ Logging appropriate

✓ Validation implemented

✓ No dead code

✓ No TODO left

✓ No commented code

✓ Architecture respected

---

# Definition of Done

A task is complete only if:

- code compiles
- tests pass
- JavaDoc written
- architecture preserved
- no duplication introduced
- logging implemented
- validation implemented
- Liquibase updated (if needed)
- API documented (if changed)
- frontend updated (if required)
- code reviewed by yourself

---

# Forbidden

Never

introduce technical debt knowingly

ignore failing tests

disable tests

leave TODOs

leave commented code

duplicate business logic

mix infrastructure with domain

place SQL inside business logic

hardcode configuration

write code "just to make it work"

Every solution should be production-ready.
