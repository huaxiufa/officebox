# Image encoding fix

The batch image processor must apply JPEG quality through ImageIO compression parameters. WebP output requires a real WebP ImageIO writer; until a WebP writer dependency is installed, the API should not label PNG bytes as `.webp`.