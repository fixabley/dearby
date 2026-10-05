import { test } from "node:test";
import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import {
  officialUrl,
  pageText,
  fetchOfficialPage,
  applicationLink,
} from "../src/source.mjs";
const hosts = ["official.example"];
test("exact HTTPS hosts, no credentials, ports, IPs; canonical tracking removal", () => {
  for (const url of [
    "http://official.example",
    "https://evil.example",
    "https://official.example.evil.org",
    "https://u:p@official.example",
    "https://official.example:444",
    "https://127.0.0.1",
  ])
    assert.throws(() => officialUrl(url, hosts));
  assert.equal(
    officialUrl("https://official.example/?z=2&utm_source=x&a=1#x", hosts),
    "https://official.example/?a=1&z=2",
  );
  assert.equal(
    pageText("<script>ignore me</script><p>A &amp; B &#xAC00;</p>"),
    "A & B 가",
  );
});
test("DNS rejects private, reserved and mixed answers; resolution has deadline", async () => {
  for (const address of [
    "127.0.0.1",
    "10.1.1.1",
    "169.254.1.1",
    "100.64.0.1",
    "192.168.1.1",
    "198.18.0.1",
    "0.0.0.0",
    "224.0.0.1",
    "::1",
  ])
    await assert.rejects(
      fetchOfficialPage("https://official.example", hosts, {
        resolve: async () => [{ address, family: 4 }],
        httpsRequest: () => assert.fail("request should not happen"),
      }),
      /public address/,
    );
  await assert.rejects(
    fetchOfficialPage("https://official.example", hosts, {
      resolve: () => new Promise(() => {}),
      timeoutMs: 20,
    }),
    /deadline/,
  );
});
function transport(response, inspect = () => {}) {
  return (url, options, callback) => {
    inspect(url, options);
    const req = new EventEmitter();
    req.destroy = (e) => req.emit("error", e);
    options.signal.addEventListener(
      "abort",
      () => req.destroy(options.signal.reason),
      { once: true },
    );
    req.end = () =>
      queueMicrotask(() => {
        const res = new EventEmitter();
        res.statusCode = response.status ?? 200;
        res.headers = response.headers ?? { "content-type": "text/html" };
        res.destroy = (e) => res.emit("error", e);
        callback(res);
        if (response.hang) return;
        res.emit("data", Buffer.from(response.html ?? "official text"));
        res.emit("end");
      });
    return req;
  };
}
const resolve = async () => [{ address: "8.8.8.8", family: 4 }];
test("TLS request is pinned and response is bounded", async () => {
  const page = await fetchOfficialPage("https://official.example", hosts, {
    resolve,
    httpsRequest: transport({}, (url, options) => {
      assert.equal(url.hostname, "official.example");
      options.lookup("official.example", {}, (err, address, family) => {
        assert.equal(err, null);
        assert.equal(address, "8.8.8.8");
        assert.equal(family, 4);
      });
    }),
  });
  assert.equal(page.text, "official text");
  await assert.rejects(
    fetchOfficialPage("https://official.example", hosts, {
      resolve,
      httpsRequest: transport({ html: "x".repeat(1000001) }),
    }),
    /1 MB/,
  );
  await assert.rejects(
    fetchOfficialPage("https://official.example", hosts, {
      resolve,
      httpsRequest: transport({ hang: true }),
      timeoutMs: 20,
    }),
    /deadline/,
  );
});
test("redirects cannot escape hosts or extend total budget", async () => {
  await assert.rejects(
    fetchOfficialPage("https://official.example", hosts, {
      resolve,
      httpsRequest: transport({
        status: 302,
        headers: { location: "https://evil.example" },
      }),
    }),
    /configured/,
  );
  await assert.rejects(
    fetchOfficialPage("https://official.example", hosts, {
      resolve,
      httpsRequest: transport({ status: 302, headers: { location: "/loop" } }),
    }),
    /redirects/,
  );
});

test("application link must be an entire anchor URL, including relative/escaped links", () => {
  const page = {
    url: "https://official.example/2026",
    html: '<a href="https://forms.gle/actualID">apply</a><a href="/apply?a=1&amp;b=2">local</a>',
  };
  assert.equal(applicationLink("https://forms.gle", page), null);
  assert.equal(
    applicationLink("https://forms.gle/actualID", page),
    "https://forms.gle/actualID",
  );
  assert.equal(
    applicationLink("https://official.example/apply?a=1&b=2", page),
    "https://official.example/apply?a=1&b=2",
  );
  assert.equal(
    applicationLink("https://evil.example", {
      ...page,
      html: '<script>"https://evil.example"</script>',
    }),
    null,
  );
});
