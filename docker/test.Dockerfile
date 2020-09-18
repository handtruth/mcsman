FROM openjdk:8-jre-alpine AS base

FROM base AS build

RUN mkdir /build \
    && apk --no-cache --no-progress add build-base git ninja python3 py-pip \
    && pip3 install meson \
    && git clone --depth 1 --branch 20190702 https://github.com/P-H-C/phc-winner-argon2.git \
    && cd /phc-winner-argon2 \
    && OPTTARGET=x86-64 make \
    && PREFIX=/build make install \
    && cd / \
    && git clone --recurse-submodules --depth 1 --branch v0.1.3 https://github.com/handtruth/tlproxy.git \
    && cd /tlproxy \
    && meson -Dprefix=/build -Dbuildtype=release -Ddefault_library=static -Doptimization=3 build \
    && cd build && ninja && ninja install

FROM base AS app

RUN mkdir /modules && mkdir /data \
    && apk --no-cache --no-progress add libstdc++ socat

WORKDIR "/data"
VOLUME "/data"
EXPOSE 1337/tcp
LABEL com.handtruth.mc.mcsman.type=mcsman
LABEL maintainer="ktlo <ktlo@handtruth.com>"
ENTRYPOINT [ "/app/bin/mcsman-core" ]

#COPY --from=build /build/bin/tlproxy /usr/local/bin/tlproxy
COPY --from=build /build/lib/x86_64-linux-gnu/libargon2.so /usr/lib/libargon2.so
COPY /app /app
COPY /modules/*-bundle-*.jar /modules/
