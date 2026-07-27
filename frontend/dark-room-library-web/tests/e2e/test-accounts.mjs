const accountSpecs = Object.freeze({
  root: {
    accountEnv: "E2E_ROOT_ACCOUNT",
    defaultAccount: "drl_root_aurora",
    passwordEnv: "E2E_ROOT_PASSWORD",
    fallbackPasswordEnv: "DRL_DEMO_ADMIN_PASSWORD",
    role: 0,
  },
  coordinator: {
    accountEnv: "E2E_COORDINATOR_ACCOUNT",
    defaultAccount: "drl_keeper_qingwu",
    passwordEnv: "E2E_COORDINATOR_PASSWORD",
    role: 1,
  },
  reader: {
    accountEnv: "E2E_READER_ACCOUNT",
    defaultAccount: "drl_reader_yandeng",
    passwordEnv: "E2E_READER_PASSWORD",
    role: 2,
  },
  purchaser: {
    accountEnv: "E2E_PURCHASER_ACCOUNT",
    defaultAccount: "drl_buyer_xinglan",
    passwordEnv: "E2E_PURCHASER_PASSWORD",
    role: 3,
  },
  logistics: {
    accountEnv: "E2E_LOGISTICS_ACCOUNT",
    defaultAccount: "drl_logistics_chenxiang",
    passwordEnv: "E2E_LOGISTICS_PASSWORD",
    role: 4,
  },
});

function requiredSecret(primaryName, fallbackName) {
  const value =
    process.env[primaryName] ||
    (fallbackName && process.env[fallbackName]) ||
    process.env.DRL_DEMO_PASSWORD;
  if (!value) {
    throw new Error(
      `Missing ${primaryName}. Set it directly or set DRL_DEMO_PASSWORD before running this script.`
    );
  }
  return value;
}

export function getAccount(name) {
  const spec = accountSpecs[name];
  if (!spec) throw new Error(`Unknown E2E account: ${name}`);
  return {
    account: process.env[spec.accountEnv] || spec.defaultAccount,
    password: requiredSecret(spec.passwordEnv, spec.fallbackPasswordEnv),
    role: spec.role,
  };
}

export function getConcurrentReaderPassword() {
  return requiredSecret("E2E_CONCURRENT_READER_PASSWORD");
}
