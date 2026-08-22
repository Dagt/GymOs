# Product Guidelines

Last reviewed: 2026-08-21.

## 1. Prose & Copywriting Style
- **Dynamic & Formal:** The tone should adapt to the context but always maintain a professional, formal baseline suitable for business operations.
- **Explanatory:** Ensure that complex administrative tasks are accompanied by clear, concise explanations to guide the user (e.g., staff or owners) through the process without confusion.
- **Language:** The application is developed **Spanish-Only**, prioritizing clear and localized terminology for gym operations in Spanish-speaking markets.

## 2. UI/UX Principles
- **Hybrid Contextual Density:** The interface must adapt its layout density based on the user's role. Administrative views (e.g., reception, POS, metrics) should be data-dense to optimize workflows for power users, while member-facing interfaces (e.g., self-service) should be spacious, accessible, and consumer-friendly.
- **Consistency:** Maintain consistent use of components and Tailwind utility classes across all modules to ensure a predictable user experience.
- **Visual system:** Administrative, configuration, automation and self-service pages use the shared dark GymFort visual language. Avoid isolated white pages unless required by an external hosted provider.
- **Branding:** Display tenant identity where configured while retaining visible GymFort attribution.

## 3. Error Handling & Feedback
- **Subdued & Non-blocking:** System feedback, warnings, and non-critical errors should be communicated through unobtrusive toast notifications or subtle status indicators. They should not interrupt the user's workflow or require immediate dismissal unless the action is destructive or critical.
- **Clarity:** Even when subdued, error messages must clearly state what went wrong and, if applicable, how the user can resolve it.
- **Placement:** Use the global floating notification system so feedback remains visible regardless of scroll position. Reserve blocking dialogs for destructive confirmation or a decision that must be made before continuing.
- **Supportability:** When available, include a correlation or support reference without exposing internal stack traces, provider secrets or sensitive payment data.
