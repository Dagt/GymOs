# Product Guide: GymFort

Last reviewed: 2026-08-21.

> `GymFort` is the customer-facing product name. `GymOS` and `gymos` remain in historical package names, database identifiers and configuration keys until a separate technical migration is approved.

## Overview

GymFort is a modular platform for operating gyms and wellness centers. It covers tenant and branch administration, staff security, onboarding, clients, memberships, benefits, access, bookings, coaching, wellness, inventory, point of sale, payments, self-service, automations, notifications, reporting and audit.

## Target audience

- Multi-branch gym organizations that need centralized governance with branch-level operation.
- Independent gyms that need an integrated operational system without assembling several tools.
- Members who use the self-service kiosk to manage memberships, bookings, contact data, card payments and recurring-payment authorization.

## Current differentiators

- Strong organization and branch isolation, with explicit permissions and audit evidence.
- Multi-branch memberships whose origin is preserved while valid access and bookings work at eligible branches.
- Self-service flows with expiring sessions and a payment lifecycle that survives browser redirects.
- Stripe Connect, Checkout, Setup, recurring charges, signed webhooks and receipts integrated in test mode.
- Durable automations and notifications. SMTP delivery exists; production-oriented Zoho Mail configuration is the next integration milestone.

## Value proposition

Reduce operational overhead while preserving control: automate repetitive work, allow members to resolve routine needs, and give owners and staff traceable operational and financial information.

## Product principles

- No membership, reservation or financial effect is considered final until the authoritative backend workflow confirms it.
- The member experience must be clear and forgiving; sensitive actions remain explicit and auditable.
- Tenant branding is supported, but GymFort attribution remains visible throughout the product.
