FROM alpine:3.18

RUN apk add --no-cache curl

COPY test-metadata.sh /test-metadata.sh
RUN chmod +x /test-metadata.sh

CMD ["/test-metadata.sh"]
