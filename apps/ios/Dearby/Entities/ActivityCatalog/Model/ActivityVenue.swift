struct ActivityVenue: Decodable {
    let phase: String
    let name: String
    let address: String?
    let coordinates: ActivityCoordinates?
}

extension ActivityVenue {
    private enum CodingKeys: String, CodingKey { case phase, name, address, coordinates }

    init(from decoder: any Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        phase = try values.decode(String.self, forKey: .phase)
        name = try values.decode(String.self, forKey: .name)
        address = try values.decodeIfPresent(String.self, forKey: .address)
        // Bad optional coordinates must not hide the original venue or whole catalog.
        coordinates = try? values.decodeIfPresent(ActivityCoordinates.self, forKey: .coordinates)
    }
}
