/**
 * ESLint configuration for the Grails VS Code Extension.
 *
 * See https://eslint.style and https://typescript-eslint.io for additional linting options.
 */
// @ts-check
import js from "@eslint/js";
import stylistic from "@stylistic/eslint-plugin";
import importPlugin from "eslint-plugin-import";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["**/.vscode-test", "**/out", "**/dist", "./server/**", "*.vsix", "node_modules/**"],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...tseslint.configs.stylistic,
  ...tseslint.configs.recommendedTypeChecked,
  {
    files: ["client/src/**/*.ts"],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      "@stylistic": stylistic,
      import: importPlugin,
    },
    settings: {
      "import/parsers": {
        "@typescript-eslint/parser": [".ts", ".tsx"],
      },
      "import/resolver": {
        typescript: {
          alwaysTryTypes: true,
          project: "./tsconfig.json",
          extensions: [".ts", ".tsx", ".js", ".jsx"],
        },
        node: {
          extensions: [".ts", ".tsx", ".js", ".jsx"],
          moduleDirectory: ["node_modules", "@types"],
        },
      },
      "import/ignore": ["vscode-languageclient/node"],
    },
    rules: {
      // VS Code recommended rules
      curly: "off",
      eqeqeq: "warn",
      "no-throw-literal": "warn",

      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "@typescript-eslint/consistent-type-imports": "warn",
      "@typescript-eslint/prefer-nullish-coalescing": "warn",
      "@typescript-eslint/prefer-optional-chain": "warn",

      "@stylistic/semi": ["warn", "always"],
      "@typescript-eslint/naming-convention": [
        "warn",
        { selector: "import", format: ["camelCase", "PascalCase"] },
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
    },
  }
);
