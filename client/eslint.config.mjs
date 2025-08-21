import typescriptEslint from "@typescript-eslint/eslint-plugin";
import tsParser from "@typescript-eslint/parser";
import importPlugin from "eslint-plugin-import";

export default [
  {
    files: ["src/**/*.ts"],
    languageOptions: {
      parser: tsParser,
      ecmaVersion: "latest",
      sourceType: "module",
    },
    plugins: {
      "@typescript-eslint": typescriptEslint,
      import: importPlugin,
    },
    settings: {
      "import/resolver": {
        typescript: {
          alwaysTryTypes: true, // resolve @types packages
        },
      },
    },
    rules: {
      // Typescript naming
      "@typescript-eslint/naming-convention": [
        "warn",
        { selector: "import", format: ["camelCase", "PascalCase"] },
      ],

      // Import validation
      "import/no-unresolved": ["error", { caseSensitive: true }],
      "import/no-duplicates": "warn",

      // Code quality
      "@typescript-eslint/no-unused-vars": "warn",
      semi: ["warn", "always"],
      curly: ["error", "all"],
      eqeqeq: "warn",
      "no-throw-literal": "warn",

      "import/order": [
        "warn",
        {
          groups: ["builtin", "external", "internal", "parent", "sibling", "index"],
          alphabetize: { order: "asc", caseInsensitive: true },
        },
      ],

      // TypeScript extras
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/consistent-type-imports": "warn",
      "@typescript-eslint/prefer-nullish-coalescing": "warn",
      "@typescript-eslint/prefer-optional-chain": "warn",
    },
  },
];
