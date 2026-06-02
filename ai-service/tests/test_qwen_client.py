import json
import os
import unittest

import httpx

from qwen_client import QwenClient


class QwenClientTest(unittest.TestCase):
    def setUp(self):
        self.previous_key = os.environ.get("DASHSCOPE_API_KEY")
        os.environ["DASHSCOPE_API_KEY"] = "test-key"

    def tearDown(self):
        if self.previous_key is None:
            os.environ.pop("DASHSCOPE_API_KEY", None)
        else:
            os.environ["DASHSCOPE_API_KEY"] = self.previous_key

    def test_json_mode_and_thinking_are_configured(self):
        captured = {}

        def handler(request):
            captured.update(json.loads(request.content))
            return httpx.Response(200, json={"choices": [{"message": {"content": "{\"reply_draft\":\"ok\"}"}}]})

        client = QwenClient(http_client=httpx.Client(transport=httpx.MockTransport(handler)))
        self.assertEqual(client.generate_json("system JSON", "user"), {"reply_draft": "ok"})
        self.assertEqual(captured["response_format"], {"type": "json_object"})
        self.assertFalse(captured["enable_thinking"])

    def test_invalid_json_is_retried_once(self):
        calls = []

        def handler(request):
            calls.append(request)
            content = "not-json" if len(calls) == 1 else "{\"reply_draft\":\"ok\"}"
            return httpx.Response(200, json={"choices": [{"message": {"content": content}}]})

        client = QwenClient(http_client=httpx.Client(transport=httpx.MockTransport(handler)))
        self.assertEqual(client.generate_json("system JSON", "user"), {"reply_draft": "ok"})
        self.assertEqual(len(calls), 2)


if __name__ == "__main__":
    unittest.main()
