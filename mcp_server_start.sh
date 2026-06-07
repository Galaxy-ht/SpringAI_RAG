npx -y supergateway \
    --stdio "npx -y @modelcontextprotocol/server-filesystem ./mcp-server" \
    --outputTransport streamableHttp --stateful \
    --sessionTimeout 60000 --port 8000