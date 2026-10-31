location ^~ /assets/ {
    root /var/www/haiyan;
    try_files $uri =404;
    limit_except GET { deny all; }
    add_header Cache-Control "public, max-age=31536000, immutable";
    add_header X-Content-Type-Options nosniff;
}

location ^~ /brand/ {
    root /var/www/haiyan;
    try_files $uri =404;
    limit_except GET { deny all; }
    add_header Cache-Control "public, max-age=86400, must-revalidate";
    add_header X-Content-Type-Options nosniff;
}

location = /favicon.svg {
    root /var/www/haiyan;
    try_files $uri =404;
    limit_except GET { deny all; }
    add_header Cache-Control "public, max-age=86400, must-revalidate";
    add_header X-Content-Type-Options nosniff;
}
