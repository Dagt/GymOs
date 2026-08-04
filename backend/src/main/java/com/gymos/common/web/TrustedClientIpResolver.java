package com.gymos.common.web;

import com.gymos.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves forwarding headers only when the direct peer belongs to an explicitly trusted proxy CIDR. */
@Component
public class TrustedClientIpResolver implements ClientIpResolver {
	private final List<Network> trustedNetworks;

	public TrustedClientIpResolver(@Value("${gymos.security.trusted-proxies:}") String configuredNetworks) {
		this.trustedNetworks = Arrays.stream(configuredNetworks.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.map(Network::parse)
				.toList();
	}

	@Override
	public String resolve(HttpServletRequest request) {
		String remoteAddress = normalizeAddress(request.getRemoteAddr());
		if (!trusted(remoteAddress)) return remoteAddress;
		List<String> forwarded = forwardedAddresses(request);
		if (forwarded.isEmpty()) return remoteAddress;

		String candidate = remoteAddress;
		for (int index = forwarded.size() - 1; index >= 0; index--) {
			if (!trusted(candidate)) break;
			String next = normalizeAddress(forwarded.get(index));
			if (next == null) break;
			candidate = next;
		}
		return candidate == null ? remoteAddress : candidate;
	}

	private List<String> forwardedAddresses(HttpServletRequest request) {
		String forwarded = request.getHeader("Forwarded");
		if (forwarded != null && !forwarded.isBlank()) {
			List<String> result = new ArrayList<>();
			for (String element : forwarded.split(",")) {
				for (String parameter : element.split(";")) {
					String[] parts = parameter.trim().split("=", 2);
					if (parts.length == 2 && "for".equalsIgnoreCase(parts[0])) result.add(parts[1]);
				}
			}
			if (!result.isEmpty()) return result;
		}
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor == null || xForwardedFor.isBlank()) return Collections.emptyList();
		return Arrays.stream(xForwardedFor.split(",")).map(String::trim).toList();
	}

	private boolean trusted(String address) {
		if (address == null) return false;
		try {
			InetAddress inetAddress = InetAddress.getByName(address);
			return trustedNetworks.stream().anyMatch(network -> network.contains(inetAddress));
		} catch (UnknownHostException exception) {
			return false;
		}
	}

	private String normalizeAddress(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String value = raw.trim().replace("\"", "");
		if (value.startsWith("[")) {
			int closing = value.indexOf(']');
			return closing > 0 ? value.substring(1, closing) : null;
		}
		if (value.chars().filter(character -> character == ':').count() == 1) {
			int portSeparator = value.lastIndexOf(':');
			if (value.substring(portSeparator + 1).matches("\\d+")) value = value.substring(0, portSeparator);
		}
		if ("unknown".equalsIgnoreCase(value) || value.startsWith("_")) return null;
		return value;
	}

	private record Network(byte[] address, int prefixLength) {
		private static Network parse(String cidr) {
			try {
				String[] parts = cidr.split("/", 2);
				byte[] address = InetAddress.getByName(parts[0]).getAddress();
				int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
				if (prefix < 0 || prefix > address.length * 8) throw new IllegalArgumentException("Invalid proxy CIDR: " + cidr);
				return new Network(address, prefix);
			} catch (UnknownHostException | NumberFormatException exception) {
				throw new IllegalArgumentException("Invalid proxy CIDR: " + cidr, exception);
			}
		}

		private boolean contains(InetAddress candidate) {
			byte[] other = candidate.getAddress();
			if (other.length != address.length) return false;
			int fullBytes = prefixLength / 8;
			int remainingBits = prefixLength % 8;
			for (int index = 0; index < fullBytes; index++) if (address[index] != other[index]) return false;
			if (remainingBits == 0) return true;
			int mask = 0xff << (8 - remainingBits);
			return (address[fullBytes] & mask) == (other[fullBytes] & mask);
		}
	}
}
