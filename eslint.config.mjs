/**
 * ESLint configuration for the Grails VS Code Extension.
 *
 * See https://eslint.style and https://typescript-eslint.io for additional linting options.
 */
// @ts-check
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import stylistic from "@stylistic/eslint-plugin";
import importPlugin from "eslint-plugin-import";

export default tseslint.config(
  {
    ignores: ["**/.vscode-test", "**/out", "**/dist", "./server/**", "*.vsix", "node_modules/**"],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...tseslint.configs.stylistic,

  {
    files: ["client/src/**/*.ts"],
    plugins: {
      "@stylistic": stylistic,
      import: importPlugin,
    },
    settings: {
      "import/resolver": {
        typescript: {
          alwaysTryTypes: true,
          project: "./client/tsconfig.json",
        },
      },
    },
    rules: {
      // VS Code recommended rules
      curly: "warn",
      "@stylistic/semi": ["warn", "always"],
      "@typescript-eslint/no-empty-function": "off",

      // TypeScript naming conventions
      "@typescript-eslint/naming-convention": [
        "warn",
        {
          selector: "import",
          format: ["camelCase", "PascalCase"],
        },
      ],

      // Unused variables (VS Code pattern)
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_",
        },
      ],

      // Your specific import rules
      "import/no-unresolved": ["error", { caseSensitive: true }],
      "import/no-duplicates": "warn",
      "import/order": [
        "warn",
        {
          groups: ["builtin", "external", "internal", "parent", "sibling", "index"],
          alphabetize: { order: "asc", caseInsensitive: true },
        },
      ],

      // Your additional TypeScript rules
      eqeqeq: "warn",
      "no-throw-literal": "warn",
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/consistent-type-imports": "warn",
      "@typescript-eslint/prefer-nullish-coalescing": "warn",
      "@typescript-eslint/prefer-optional-chain": "warn",
    },
  }
);
