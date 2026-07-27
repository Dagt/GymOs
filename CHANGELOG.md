# Changelog

All notable changes to GymOS will be documented in this file.

## 0.7.0-pilot-readiness - Unreleased

- Consolidate documentation into one canonical status file per module.
- Add the live technical-debt register and audited branch chronology.
- Advance Coaches and workouts editing, exercise aliases and assignment lifecycle.
- Redesign Team, workout builder, exercise catalog and personal Coach metrics
  with compact, progressive and role-aware interfaces.
- Add organization-wide reusable exercise equipment, a scrollable catalog and
  compact fixed-height exercise cards whose descriptions do not stretch rows.
- Keep muscle-group and equipment selectors aligned by rendering new-equipment
  details outside the identification grid.
- Restore the completed Wellness directory, agenda, appointments and role
  access from `feature/wellness-services` on top of the current platform.
- Add live member lookup for workout assignments and align assignment eligibility
  with the `PUBLISHED` status produced by the publishing workflow.
- Rework the workout builder into three progressive steps and move exercise
  descriptions to an on-demand preview.
- Harden production readiness configuration.
- Add operational runbooks for deployment, backup, restore, rollback, incidents and maintenance.
- Restrict demo seed to the local Spring profile.
- Expand CI with Docker Compose validation and image builds.

## 0.6.5

- Redesign frontend entry and administration experience.
- Add local brand customization for demos and pilots.

## 0.6.0

- Add onboarding and CSV client import flows.
